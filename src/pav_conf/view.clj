(ns pav-conf.view
  "Main view with actions."
  (:import [com.vaadin.server Sizeable$Unit FontAwesome]
           [com.vaadin.ui LegacyWindow Label VerticalLayout HorizontalLayout Alignment HorizontalSplitPanel
            Tree Label Table])
  (:require [pav-conf.conf :as c]
            [pav-conf.convox :as x]
            [pav-conf.events :as e]))

;;; right view

(defn- update-rack-details
  "Fetch rack details and paint table with them."
  [^VerticalLayout view]
  (let [instances  (x/get-instances (c/read-creds))
        system     (x/get-system (c/read-creds))
        inst-table (Table. "Instances")
        sys-table  (Table. "System details")]
    (doto inst-table
      (.setSizeFull)
      (.setSelectable true)
      (.setPageLength 0)
      (.addContainerProperty "ID" String nil)
      (.addContainerProperty "Processes" Long nil)
      (.addContainerProperty "CPU" Long nil)
      (.addContainerProperty "Memory" Double nil)
      (.addContainerProperty "Agent" Boolean nil)
      (.addContainerProperty "Public IP" String nil)
      (.addContainerProperty "Private IP" String nil)
      (.addContainerProperty "Status" String nil))
    
    (doto sys-table
      (.setSizeFull)
      (.setSelectable true)
      (.setPageLength 0)
      (.addContainerProperty "Name" String nil)
      (.addContainerProperty "Region" String nil)
      (.addContainerProperty "Count" Long nil)
      (.addContainerProperty "Version" String nil)
      (.addContainerProperty "Type" String nil)
      (.addContainerProperty "Status" String nil))

    ;; populate instances table
    (loop [instances instances, i 0]
      (when-let [instance (first instances)]
        (.addItem inst-table
                  (to-array 
                   (map (fn [i]
                          (let [val (get instance i)]
                            (if (= i "memory")
                              (double val)
                              val)))
                        ["id" "processes" "cpu" "memory" "agent" "public-ip" "private-ip" "status"]))
                  i)
        (recur (rest instances) (inc i))))
    
    ;; populate system details table
    (as-> ["name" "region" "count" "version" "type" "status"] $
         (map system $)
         (to-array $)
         (.addItem sys-table $ 0))

    (doto view
      (.setSpacing true)
      (.addComponent (doto (Label. "Rack details")
                       (.setStyleName "h2")))
      (.addComponent inst-table)
      (.addComponent sys-table))))

(defn- update-app-details
  "Fetch application details and paint table with them."
  [^VerticalLayout view node]
  (let [env   (x/get-app-environment (c/read-creds) node)
        table (Table.)]

    (doto table
      (.setSizeFull)
      (.setPageLength 10)
      (.setSelectable true)
      (.addContainerProperty "Variable" String nil)
      (.addContainerProperty "Value" String nil))
    
    (loop [env env, i 0]
      (when-let [kv (first env)]
        (.addItem table (to-array kv) i)
        (recur (rest env) (inc i))))

    (doto view
      (.setSpacing true)
      (.addComponent (doto (Label. "Application variables")
                       (.setStyleName "h2")))
      (.addComponent table))))

(defn- update-right-view!
  "Repaint right view with table and other details, depending on tree node.
If root is selected, that means user clicked 'Rack' and we will fetch all rack server instances, painting
specific table. If any of nodes is selected, that means user clicked specific application, fetching
necessary details, painting own table."
  [^VerticalLayout view node root?]
  (.removeAllComponents view)
  (if root?
    (update-rack-details view)
    (update-app-details view node)))

;;; left tree

(defn- push-node!
  "Append node on given tree, with given root."
  [^Tree tree ^String root ^String name icon]
  (doto tree
    (.addItem name)
    (.setParent name root)
    (.setChildrenAllowed name false)
    (.setItemIcon name icon)))

(defn- build-apps-tree
  "Create applications tree."
  [^VerticalLayout right-view]
  (let [tree (Tree. "rack-tree")
        root "Rack"
        data (x/get-apps (c/read-creds))]
    (.addItem tree root)
    (doseq [item data]
      (push-node! tree root (get item "name" "<unknown>") (if (x/running? item)
                                                            FontAwesome/CHECK
                                                            FontAwesome/CLOSE)))
    ;; on every tree item click, this event handler is invoked
    (e/with-tree-item-event tree
      (let [node  (.getValue tree)
            root? (not (.getParent tree node))]
        (update-right-view! right-view node root?)))
    tree))

(defn build-main-view!
  "Create main view in given window. Returns modified window object."
  [^LegacyWindow win]
  (let [layout (VerticalLayout.)
        right-view (doto (VerticalLayout.)
                     (.setStyleName "right-view"))
        toolbar (doto (HorizontalLayout.)
                  (.setWidth "100%")
                  (.setHeight "50px")
                  (.addComponent (doto (Label. "PAV Configuration Manager")
                                   (.setStyleName "h2"))))
        view    (doto (HorizontalSplitPanel.)
                  (.setSplitPosition 210.0 Sizeable$Unit/PIXELS)
                  (.setFirstComponent (build-apps-tree right-view))
                  (.setSecondComponent right-view))]
    (doto layout
      (.setSizeFull)
      (.addComponent toolbar)
      (.addComponent view)
      (.setExpandRatio view 1)
      (.setSpacing true)
      (.setStyleName "main-view")
      (.setComponentAlignment toolbar Alignment/TOP_LEFT)
      (.setComponentAlignment view Alignment/TOP_LEFT))
    (.setContent win layout)
    win))
