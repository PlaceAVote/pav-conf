(ns pav-conf.conf
  "Main configuration reader."
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [environ.core :refer [env]]))

(defn- parse-env-creds
  "Parse environment credentials, stored in CONVOX_CREDS. They are expected to be
in form 'NAME1:PASS1;NAME2:PASS2;...'. Obviously, take care of having semicolon or
colons in passwords. Returns a map in form '{:name XXX, :password YYY}'."
  ([creds]
     (when creds
       (map (fn [e]
              (zipmap [:name :password] (s/split e #"\s*:\s*")))
            (s/split creds #"\s*;\s*"))))
  ([] (-> :convox-creds env parse-env-creds)))

(defn- read-conf-raw
  "Read configuration and return map from it."
  []
  (-> "pav-conf.edn" io/resource slurp read-string))

(def ^{:doc "Memoized version of read-conf-raw. Use it to access global configuration."
       :public true}
  read-conf (memoize read-conf-raw))

(defn- read-creds-raw
  "Read credentials and convert them to expected convox API format. If password
is not present, consult environment for it."
  [name]
  (let [ret (->> (read-conf)
                 :convox-hosts
                 (filter #(= name (:name %)))
                 first)]
    (when-not (:password ret)
      (->> (parse-env-creds)
           (some #(when (= name (:name %)) %))
           (merge ret)))))

(def ^{:doc "Memoized version of read-creds-raw."
       :public true}
  read-creds (memoize read-creds-raw))

(defn all-convox-racks
  "Return a list of all configuration registered Convox racks."
  []
  (map :name (:convox-hosts (read-conf))))
