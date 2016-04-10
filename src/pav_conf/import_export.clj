(ns pav-conf.import-export
  "Import and export facility for variables."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log])
  (:import [com.vaadin.ui Button Upload Upload$Receiver Upload$FinishedListener]
           [com.vaadin.server FileDownloader StreamResource StreamResource$StreamSource]
           [java.io ByteArrayInputStream File FileOutputStream]))

(defn- ^String pprint-str
  "Pretty print given object."
  [obj]
  (with-out-str
    (pprint obj)))

(defn- keywordize
  "Convert name to clojure keyword."
  [in]
  (if (keyword? in)
    in
    (-> in str .toLowerCase (.replaceAll "[_ ]" "-") keyword)))

(defn- unkeywordize
  "Convert name/keyword to uppercase string."
  [in]
  (-> in name .toUpperCase (.replaceAll "-" "_")))

(defn- alter-map-keys
  "Modify map keys using provided function."
  [mp func]
  (into {} (for [[k v] mp]
             [(func k) v])))

(defn attach-exporter
  "Invoke exporting when user clicks a button. This is a bit different than
usual Vaadin events mainly due FileDownloader class."
  [^Button button export-fn]
  (let [downloader (FileDownloader.
                    (StreamResource.
                     (reify StreamResource$StreamSource
                       (getStream [this]
                         (-> (export-fn)
                             (alter-map-keys keywordize)
                             pprint-str
                             (.getBytes "UTF-8")
                             ByteArrayInputStream.)))
                     "export.edn"))]
    (.extend downloader button)))

(defn upload-btn
  "Create upload button with all the gory details."
  [label description finished-fn error-fn]
  (let [tmpfile (atom nil)
        upload (Upload. nil
                        (reify Upload$Receiver
                          (receiveUpload [this filename mime-type]
                            (reset! tmpfile (File/createTempFile "pav-conf-upload." ".tmp"))
                            (FileOutputStream. ^File @tmpfile))))]
    (doto upload
      (.setImmediate true) ;; start upload as soon as file is selected
      (.setButtonCaption label)
      (.setDescription description)
      (.addListener
       (reify Upload$FinishedListener
         (uploadFinished [this event]
           (try
             (some-> @tmpfile
                     slurp
                     edn/read-string
                     (alter-map-keys unkeywordize)
                     finished-fn)
             (catch Exception e
               (log/errorf e "Failed to upload '%s'" @tmpfile)
               (when error-fn
                 (-> e .getMessage error-fn)))
             (finally
               (log/debugf "Cleaning '%s' after successful upload" tmpfile)
               (when @tmpfile
                 (.delete ^File @tmpfile))))))))))
