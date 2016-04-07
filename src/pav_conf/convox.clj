(ns pav-conf.convox
  "Convox API wrapper."
  (require [clj-http.client :as cli]
           [cheshire.core :as json]
           [clojure.string :as str]
           [clojure.tools.logging :as log]))

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
      (-> ret :body json/parse-string)
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

(defn- mp->convox-body
  "Convert map to string, joining key/value combinations with newlines."
  [mp]
  (->> mp
       (map #(str (name (key %)) "=" (val %)))
       (str/join "\n")))

(defn promote-release
  "Promote given release for application."
  [creds app release-id]
  (let [ret (cli/post (format "https://%s/apps/%s/releases/%s/promote" (:host creds) app release-id)
                      {:basic-auth ["convox" (:password creds)]
                       :body nil ;; no body
                       :insecure? true
                       :headers {"Version" convox-cli-version}})]
    (= 200 (:status ret))))

(defn set-env
  "Set variables set in map. Note that Convox does not accept correctly urlencoded body
but form: 'FOO=1\nBAZ=2' for multiple key/values. If promote? was set to true, it will
push changes and return vector with release id and full reply.

If promote? was false, nothing will be pushed and will be returned only reply from post
request."
  ([creds app mp promote?]
     (let [ret (cli/post (format "https://%s/apps/%s/environment" (:host creds) app)
                         {:basic-auth ["convox" (:password creds)]
                          :body (mp->convox-body mp)
                          :insecure? true
                          :headers {"Version" convox-cli-version}})
           success? (= 200 (:status ret))]
       (if (and success? promote?)
         (let [release-id (get-in ret [:headers "Release-Id"])]
           (log/infof "Promoting released '%s'..." release-id)
           (log/infof "Promoting done with status: %s" (promote-release creds app release-id))
           [release-id ret])
         ret)))
  ([creds app mp] (set-env creds app mp true)))

(defn running?
  "Check if server entry is running propertly."
  [item]
  (= "running" (get item "status")))
