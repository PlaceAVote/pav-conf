(ns pav-conf.tree
  "UI tree representing all running apps on given rack."
  (:import [com.vaadin.ui Tree Label])
  (:require [pav-conf.conf :as c]
            [pav-conf.convox :as x]))

(defn- push-node!
  "Append node on given tree, with given root."
  [^Tree tree ^String root ^String name]
  (doto tree
    (.addItem name)
    (.setParent name root)
    (.setChildrenAllowed name false)))

(defn build-apps-tree
  "Create applications tree."
  []
  (let [tree (Tree. "rack-tree")
        root "Rack"
        data (x/get-apps (c/read-creds))]
    (.addItem tree root)
    (doseq [item data]
      (push-node! tree root (get item "name" "<unknown>")))
    tree))
