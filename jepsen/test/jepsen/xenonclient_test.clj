(ns jepsen.xenonclient-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :refer :all]
            [clojure.pprint :refer [pprint]]
            [jepsen.xenonclient :as x]))

(def c (x/connect "http://127.0.0.1:8000"))

; Delete all data before each test
(use-fixtures :each #(do (x/delete-all! c nil) (%)))

(deftest casv-test!
  (let [k (str (java.util.UUID/randomUUID))]
    (x/reset! c k "hi")
    (is (false? (x/casv! c k 10 "next")))
    (is (= "hi" (x/get c k)))
    (x/casv! c k 0 "hello")
    (is (= "hello" (x/get c k)))))

(deftest cas-test!
  (let [k (str (java.util.UUID/randomUUID))]
    (x/reset! c k "hi")
    (is (false? (x/cas! c k "test" "next")))
    (is (= "hi" (x/get c k)))
    (is (true? (x/cas! c k "hi" "hello")))
    (is (= "hello" (x/get c k))))
  (x/reset! c "foo" "init")
  (is (false? (x/cas! c "foo" "nope" "next")))
  (is (= "init" (x/get c "foo")))

  (x/cas! c "foo" "init" "next")
  (is (= "next" (x/get c "foo"))))


(deftest list-directory
  (is (= (x/get c nil)
        nil))

  (x/reset! c "foo" 1)
  (x/reset! c "bar" 2)

  (is (= (x/get-all-keys c)
        ["foo" "bar"])))

(deftest reset-get-test
  (testing "a simple key"
    (x/reset! c "foo" "hi")
    (is (= "hi" (x/get c "foo")))))

(deftest create-test!
  (let [r (str (java.util.UUID/randomUUID))
        kk (str (rand-int 500))
        k (x/create! c kk r)]
    (is (= kk k))
    (is (= r (x/get c k)))))

(deftest getv-test
  (let [val "hello"
        key (str (java.util.UUID/randomUUID))]
    (x/create! c key val)
    (is (= (list val 0) (x/getv c key)))
    (x/reset! c key val)
    (is (= (list val 1) (x/getv c key)))
    (x/reset! c key val)
    (is (= (list val 2) (x/getv c key)))))
