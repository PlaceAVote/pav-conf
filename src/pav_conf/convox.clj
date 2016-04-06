(ns pav-conf.convox
  "Convox API wrapper."
  (require [clj-http.client :as cli]
           [clojure.data.json :as json]))

(def ^{:private true
       :doc "Internal version of application, to match (or be less than) Convox API."}
  convox-cli-version "20160210165552")

;; convox-pav-dev-629155429.us-east-1.elb.amazonaws.com
;; DjJRSfKPKmAsZRlEsjDNDNCxBCjNtQ

(defn- my-get
  "Common get version with standard options. Parse response string as json
if applicable."
  [host auth]
  (let [ret (cli/get host {:basic-auth ["convox" auth]
                           :insecure? true
                           :accept :json
                           :headers {"Version" convox-cli-version
                                     "Content-Type" "application/json"}})]
    (if (= 200 (:status ret))
      (-> ret :body json/read-str)
      (throw (ex-info (format "Failed GET request to '%s'" host) ret)))))

(defn get-apps
  "Return map of all running applications on given host."
  [creds]
  (my-get (format "https://%s/apps" (:host creds)) (:password creds)))

(defn get-app-environment
  "Return environment variables for given application."
  [creds app]
  (my-get (format "https://%s/apps/%s/environment" (:host creds) app) (:password creds)))

(defn get-instances
  "Return running instances on logged in rack."
  [creds]
  (my-get (format "https://%s/instances" (:host creds)) (:password creds)))

(defn get-system
  "Return system details."
  [creds]
  (my-get (format "https://%s/system" (:host creds)) (:password creds)))

(defn running?
  "Check if server entry is running propertly."
  [item]
  (= "running" (get item "status")))
