(ns pav-conf.conf
  "Main configuration reader."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn- read-conf-raw
  "Read configuration and return map from it."
  []
  (-> "pav-conf.edn" io/resource slurp read-string))

(def ^{:doc "Memoized version of read-conf-raw. Use it to access global configuration."
       :public true}
  read-conf (memoize read-conf-raw))

(defn- read-creds-raw
  "Read credentials and convert them to expected convox API format."
  []
  {:host     (:convox-api-host (read-conf))
   :password (:convox-api-key  (read-conf))})

(def ^{:doc "Memoized version of read-creds-raw."
       :public true}
  read-creds (memoize read-creds-raw))

