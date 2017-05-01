(ns jepsen.xenon
  (:gen-class)
  (:require [clojure.tools.logging   :refer :all]
            [clojure.string          :as str]
            [clojure.data.json       :as json]
            [clj-http.client         :as httpclient]
            [jepsen.xenonclient      :as x]
            [slingshot.slingshot     :refer [try+]]
            [potemkin                :refer [definterface+]]
            [knossos.model           :as model]
            [jepsen [checker         :as checker]
             [cli                    :as cli]
             [client                 :as client]
             [control                :as c]
             [db                     :as db]
             [generator              :as gen]
             [nemesis                :as nemesis]
             [core                   :as jepsen]
             [tests                  :as tests]
             [util                   :as util :refer [timeout]]
             [independent            :as independent]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.control.net      :as net]
            [jepsen.control.util     :as cu]
            [jepsen.os.debian        :as debian]))

(def dir "/opt/xenon")
(def binary "/usr/bin/java")
(def jarurl "https://www.dropbox.com/s/51894h03ayt6xtq/")
(def xenon-version "1.5.0-SNAPSHOT")
(def logfile (str dir "/xenon.log"))
(def pidfile (str dir "/xenon.pid"))

(defn node-url
  "An HTTP url for connecting to a node on a particular port."
  [node port]
  (str "http://" (name node) ":" port))

(defn peer-url
  "The HTTP url for other peers to talk to a node."
  [node]
  (node-url node 8000))

(defn client-url
  "The HTTP url clients use to talk to a node."
  [node]
  (node-url node 8000))

(defn initial-cluster
  "Constructs an initial cluster string for a test, like
  \"node1:8000,node2:8000,...\""
  [test]
  (->> (:nodes test)
    (map (fn [node]
           (str (peer-url node))))
    (str/join ",")))

(defn examples-url
  "The HTTP url clients use to talk to a node."
  [node]
  (str (node-url node 8000) "/core/examples"))

(defn parse-long
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (Long/parseLong s)))

(defn db
  "Xenon host for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing xenon" version)
      (c/exec :mkdir :-p dir)
      (c/cd dir
        (c/su
          (let [url (str jarurl "xenon-host-" version "-jar-with-dependencies.jar")
                dest (str dir "/xenon-host-" version "-jar-with-dependencies.jar")]
            (c/exec :wget (str url "-O" dest)))
          (cu/start-daemon!
            {:logfile logfile
             :pidfile pidfile
             :chdir dir}
            binary
            :-cp (str "./xenon-host-" version "-jar-with-dependencies.jar")
            :com.vmware.xenon.host.DecentralizedControlPlaneHost (str "--id=" (name node))
            (str "--port=" 8000)
            (str "--adminPassword=" (str "test123"))
            (str "--bindAddress=" (net/ip (name node)))
            (str "--publicUri=http://" (name node) ":8000")
            (str "--sandbox=" (str dir "/sandbox/xenon"))
            (str "--peerNodes=" (initial-cluster test)))

          (jepsen/synchronize test)
          (Thread/sleep 10000))))

    (teardown! [_ test node]
      (info node "tearing down xenon")
      (cu/stop-daemon! binary pidfile)
      (c/su
        (c/exec :rm :-rf (str dir "/sandbox/xenon"))))

    db/LogFiles
    (log-files [_ test node]
      [logfile])))

(defn r [_ _] {:type :invoke, :f :read, :value nil})
(defn q [_ _] {:type :invoke, :f :query, :value nil})
(defn w [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(defn client
  "A client for a single compare-and-set register"
  [conn node]
  (reify client/Client
    (setup! [_ test node]
      (client (x/connect (client-url node)
                {:timeout 5000}) node))

    (invoke! [this test op]
      (let [[k v] (:value op)]
        (try+
          (case (:f op)
            :read (let [value (parse-long (x/get conn k))]
                    (assoc op :type :ok , :value (independent/tuple k value)))
            :write (do (x/reset! conn (str k) (str v))
                     (assoc op :type , :ok))
            :cas (let [[value value'] v]
                   (assoc op :type (if (x/cas! conn k value value')
                                     :ok :fail))))
          (catch java.net.SocketTimeoutException e
            (assoc op
              :type (if (= :read (:f op)) :fail :info)
              :error :timeout))
          (catch [:status 408] e
            (assoc op
              :type :fail :error :timeout))
          (catch [:status 409] e
            (assoc op
              :type :fail :error :conflict))
          (catch [:status 500] e
            (assoc op
              :type :fail :error :internal))
          (catch [:status 404] e
            (assoc op :type :fail , :error :not-found)))))

    (teardown! [_ test])))


(defn xenon-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
    {:name "xenon"
     :os debian/os
     :db (db xenon-version)
     :client (client nil nil)
     :nemesis (nemesis/partition-random-halves)
     :model (model/cas-register)
     :generator (->> (independent/concurrent-generator
                       10
                       (range)
                       (fn [k]
                         (->> (gen/mix [r w cas])
                           (gen/stagger 1/10)
                           (gen/limit 100))))
                  (gen/nemesis
                    (gen/seq (cycle [(gen/sleep 5)
                                     {:type :info, :f :start}
                                     (gen/sleep 5)
                                     {:type :info, :f :stop}])))
                  (gen/time-limit (:time-limit opts)))
     :checker (checker/compose
                {:perf (checker/perf)
                 :indep (independent/checker
                          (checker/compose
                            {:timeline (timeline/html)
                             :linear checker/linearizable}))})}
    opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn xenon-test})
              (cli/serve-cmd))
    args))
