(ns pav-conf.import-export
  "Import and export facility for variables."
  (:require [clojure.pprint :refer [pprint]])
  (:import [com.vaadin.ui Button]
           [com.vaadin.server FileDownloader StreamResource StreamResource$StreamSource]
           java.io.ByteArrayInputStream))

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

(defn- keywordize-map
  "Make sure all keys in map are in keyword format."
  [mp]
  (into {} (for [[k v] mp]
             [(keywordize k) v])))

(defn attach-exporter
  "Invoke exporting when user clicks a button. This is a bit different than
usual Vaadin events mainly due FileDownloader class."
  [^Button button export-fn]
  (let [downloader (FileDownloader.
                    (StreamResource.
                     (reify StreamResource$StreamSource
                       (getStream [this]
                         (-> (export-fn)
                             keywordize-map
                             pprint-str
                             (.getBytes "UTF-8")
                             ByteArrayInputStream.)))
                     "export.edn"))]
    (.extend downloader button)))
