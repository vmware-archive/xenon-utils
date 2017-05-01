(ns jepsen.xenonclient
  "Core Raft API operations over HTTP. Clients are currently stateless, but you
  may maintain connection pools going forward. In general, one creates a client
  using (connect) and uses that client as the first argument to all API
  functions.

  Functions with a bang, like reset!, mutate state. All other functions are
  pure.

  Some functions come in pairs, like get and get*.

  The get* variant returns the full xenon response body as a map. Note that
  values are strings; cjxenon does not provide value
  serialization/deserialization yet.

  The get variant returns a more streamlined representation: just the node
  value itself."
  (:refer-clojure :exclude [swap! reset! get set])
  (:require [clojure.tools.logging :refer :all]
            [clojure.core          :as core]
            [clojure.core.reducers :as r]
            [clojure.string        :as str]
            [clojure.java.io       :as io]
            [clj-http.client       :as http]
            [clj-http.util         :as http.util]
            [cheshire.core         :as json]
            [clojure.data.json     :as cjson]
            [slingshot.slingshot   :refer [try+ throw+]])
  (:import (com.fasterxml.jackson.core JsonParseException)
           (java.io InputStream)
           (clojure.lang MapEntry)))

(def factory ["core" "examples"])
(def default-timeout "milliseconds" 1000)
(def debugging false)

(defn connect
  "Creates a new xenon client for the given server URI. Example:

  (def xenon (connect \"http://127.0.0.1:4001\"))"
  ([server-uri]
    (connect server-uri {}))
  ([server-uri opts]
    (merge {:endpoint server-uri}
      opts)))

(defn base-url
  "Constructs the base URL for all etcd requests. Example:

  (base-url client) ; => \"http://127.0.0.1:4001/v2\""
  [client]
  (str (:endpoint client)))

(defn decompose-string
  "Splits a string on slashes, ignoring any leading slash."
  [s]
  (let [s (if (.startsWith s "/") (subs s 1) s)]
    (str/split s #"/")))

(defn ^String normalise-key-element
  "String, symbol, and keyword keys map to their names;
  e.g. \"foo\", :foo, and 'foo are equivalent.

  Numbers map to (str num)."
  [key]
  (cond
    (string? key) (if (re-find #"/" key)
                    (decompose-string key)
                    [key])
    (instance? clojure.lang.Named key) [(name key)]
    (number? key) [(str key)]
    :else (throw (IllegalArgumentException.
                   (str "Don't know how to interpret " (pr-str key)
                     " as key")))))

(defn normalise-key
  "Return the key as a sequence of key elements.  A key can be
  specified as a string, symbol, keyword or sequence thereof.
  A nil key maps to [\"\"], the root."
  [key]
  (cond
    (nil? key) [""]
    (sequential? key) (mapcat normalise-key-element key)
    (string? key) (decompose-string key)
    :else (normalise-key-element key)))

(defn prefix-key
  "For a given key, return a key sequence, prefixed with the given key element."
  ([key]
    (concat (normalise-key key)))
  ([prefix key]
    (concat (normalise-key prefix) (normalise-key key))))

(defn ^String encode-key-seq
  "Return a url-encoded key string for a key sequence."
  [key-seq]
  (str/join "/" (map http.util/url-encode key-seq)))

(defn ^String url
  "The URL for a key under a specified root-key.

  (url client [\"keys\" \"foo\"]) ; => \"http://127.0.0.1:4001/v2/keys/foo"
  [client key-seq]
  (str (base-url client) "/" (encode-key-seq key-seq)))

(defn remap-keys
  "Given a map, transforms its keys using the (f key). If (f key) is nil,
  preserves the key unchanged.

  (remap-keys inc {1 :a 2 :b})
  ; => {2 :a 3 :b}

  (remap-keys {:a :a'} {:a 1 :b 2})
  ; => {:a' 1 :b 2}"
  [f m]
  (->> m
    (r/map (fn [[k v]]
             [(let [k' (f k)]
                (if (nil? k') k k'))
              v]))
    (into {})))

(defn http-opts
  "Given a map of options for a request, constructs a clj-http options map.
  :timeout is used for the socket and connection timeout. Remaining options are
  passed as query params."
  [client opts]
  {:as :string
   :throw-exceptions? true
   :follow-redirects true
   :force-redirects true ; Etcd uses 307 for side effects like PUT
   :socket-timeout (or (:timeout opts) (:timeout client))
   :conn-timeout (or (:timeout opts) (:timeout client))
   :query-params (dissoc opts :timeout :root-key)})

(defn parse-json
  "Parse an inputstream or string as JSON"
  [str-or-stream]
  (if (instance? InputStream str-or-stream)
    (json/parse-stream (io/reader str-or-stream) true)
    (json/parse-string str-or-stream true)))

(defn parse-resp
  "Takes a clj-http response, extracts the body, and assoc's status and Raft
  X-headers as metadata (:etcd-index, :raft-index, :raft-term) on the
  response's body."
  [response]
  (when-not (:body response)
    (throw+ {:type ::missing-body
             :response response}))

  (try+
    (let [body (parse-json (:body response))
          h (:headers response)]
      (with-meta body
        {:status (:status response)}))
    (catch JsonParseException e
      (throw+ {:type ::invalid-json-response
               :response response}))))

(defmacro parse
  "Parses regular responses using parse-resp, but also rewrites slingshot
  exceptions to have a little more useful structure; bringing the json error
  response up to the top level and merging in the http :status."
  [expr]
  `(try+
     (let [r# (parse-resp ~expr)]
       r#)
     (catch (and (:body ~'%) (:status ~'%)) {:keys [:body :status] :as e#}
       ; etcd is quite helpful with its error messages, so we just use the body
       ; as JSON if possible.

       (try (let [body# (parse-json ~'body)]
              (throw+ (cond (string? body#) {:message body# :status ~'status}
                        (map? body#) (assoc body# :status ~'status)
                        :else {:body body# :status ~'status})))
         (catch JsonParseException _#
           (throw+ e#))))))

(declare node->value)

(defn node->pair
  "Transforms an etcd node representation of a directory into a [key value]
  pair, recursively. Prefix is the length of the key prefix to drop; etcd
  represents keys as full paths at all levels."
  [prefix-len node]
  (MapEntry. (subs (:key node) prefix-len)
    (node->value node)))

(defn node->value
  "Transforms an etcd node representation into a value, recursively. Prefix is
  the length of the key prefix to drop; etcd represents keys as full paths at
  all levels."
  ([node] (node->value 1 node))
  ([prefix node]
    (if (:nodes node)
      ; Recursive nested map of relative keys to values
      (let [prefix (if (= "/" (:key node))
                     1
                     (inc (.length (:key node))))]
        (->> node
          :nodes (r/map (partial node->pair prefix))
          (into {})))
      (:value node))))

(defn get*
  ([client key]
    (get* client key {}))
  ([client key opts]
    (->> opts
      (remap-keys {})
      (http-opts client)
      (http/get (url client (prefix-key factory key)) {:debug debugging})
      (:body)
      (parse-json)
      )))

(defn get
  "Gets the current value of a key. If the key does not exist, returns nil."
  ([client key]
    (get client key {}))
  ([client key opts]
    (try+
      (-> (get* client key opts) (:name))
      (catch [:status 404] _ nil))))

(defn get-all-keys
  ([client]
    (get-all-keys client {}))
  ([client opts]
    (try+
      (->> (get* client nil opts)
        (:documentLinks)
        (map (fn [x] (last (str/split x #"/")))))
      (catch [:status 404] _ nil))))

(defn getv
  ([client key]
    (getv client key {}))
  ([client key opts]
    (try+
      (vals (select-keys (get* client key opts) [:name :documentVersion]))
      (catch [:status 404] _ nil))))

(def h {"pragma" "xn-force-index-update"})

(defn reset!
  "Resets the current value of a given key to `value`."
  ([client key value]
    (reset! client key value {}))
  ([client key value opts]
    (->> (assoc opts :value value)
      (http-opts client)
      (http/post (url client (prefix-key factory))
        { :body (str "{ name: " value ", documentSelfLink: " (name key) "}")
          :headers h
          :content-type :json
          :debug debugging})
      (:body)
      (parse-json))))

(defn create!*
  ([client path value]
    (create!* client path value {}))
  ([client path value opts]
    (->> (assoc opts :value value)
      (http/post (url client (prefix-key factory))
        { :body (str "{ name: " value ", documentSelfLink: " (name path) "}")
          :headers h
          :content-type
          :json
          :debug debugging})
      (:body)
      (parse-json)
      )))

(defn create!
  "Creates a new, automatically named object under the given path with the
  given value, and returns the full key of the created object. Options:

  :timeout
  :ttl"
  ([client path value]
    (create! client path value {}))
  ([client path value opts]
    (-> (create!* client path value opts)
      :documentSelfLink (decompose-string)
      (last)
      )))

(defn delete!
  "Deletes the given key. Options:

  :timeout
  :dir?
  :recursive?"
  ([client key]
    (delete! client key {}))
  ([client key opts]
    (->> opts
      (http-opts client)
      (http/delete (url client (prefix-key factory key))))))

(defn delete-all!
  "Deletes all nodes, recursively if necessary, under the given directory.
  Options:

  :timeout"
  ([client key]
    (delete-all! client key {}))
  ([client key opts]
    (doseq [node (->> (get-all-keys client key)
                   )]
      (delete! client (name node)))))

(defn casv!
  "Compare and set based on the current value. Updates key to be value' iff the
  current value of key is value. Optionally, you may also constrain the
  previous index and/or the existence of the key. Returns false for CAS failure.
"
  ([client key value value']
    (casv! client key value value' {}))
  ([client key value value' opts]
    (try+
      (http/patch (url client (prefix-key factory key))
        { :body (str "{ kind: 'com:vmware:xenon:services:common:ExampleService:StrictUpdateRequest', name: " value' ", documentVersion:" value "}")
          :headers h
          :content-type :json
          :debug debugging})
      (catch [:status 400] _ false))))

(defn cas!
  "Compare and set based on the current value. Updates key to be value' iff the
  current value of key is value. Optionally, you may also constrain the
  previous index and/or the existence of the key. Returns false for CAS failure.
"
  ([client key value value']
    (cas! client key value value' '{}))
  ([client key value value' opts]
    (try+
      (
        let [[val ver] (getv client key)]
        (println (class val) " " (class value))
        (println val " " value)
        (if (= val (str value))
          (boolean (http/patch (url client (prefix-key factory key))
                     { :body (str "{ kind: 'com:vmware:xenon:services:common:ExampleService:StrictUpdateRequest', name: " value' ", documentVersion:" ver "}")
                       :headers h
                       :content-type :json
                       :debug debugging}))
          false))
      (catch [:status 400] _ false))))