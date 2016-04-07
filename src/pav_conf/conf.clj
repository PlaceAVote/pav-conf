(ns pav-conf.conf
  "Main configuration reader."
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
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
  ([name]
     (->> (read-conf)
          :convox-hosts
          (filter #(= name (:name %)))
          first)))

(def ^{:doc "Memoized version of read-creds-raw."
       :public true}
  read-creds (memoize read-creds-raw))

(defn all-convox-racks
  "Return a list of all configuration registered Convox racks."
  []
  (map :name (:convox-hosts (read-conf))))
