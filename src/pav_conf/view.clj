(ns pav-conf.view
  "Main view with actions."
  (:import [com.vaadin.server Sizeable$Unit FontAwesome Page]
           [com.vaadin.ui LegacyWindow Label VerticalLayout HorizontalLayout Alignment HorizontalSplitPanel
            Tree Label Table Button CssLayout Notification]
           com.vaadin.shared.ui.label.ContentMode)
  (:require [pav-conf.conf :as c]
            [pav-conf.convox :as x]
            [pav-conf.events :as e]
            [pav-conf.var-editor :as ve]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(defn- display-notification
  "Display notification."
  [title label]
  (doto (Notification. title label Notification/TYPE_TRAY_NOTIFICATION)
    (.show (Page/getCurrent))))

;;; right view

(defn- update-rack-details
  "Fetch rack details and paint table with them."
  [^VerticalLayout view]
  (log/info "Fetching rack details...")
  (let [instances  (x/get-instances (c/read-creds))
        system     (x/get-system (c/read-creds))
        inst-table (Table. "Instances")
        sys-table  (Table. "System details")]

    (doto inst-table
      (.setSizeFull)
      (.setSelectable true)
      (.setPageLength 0)
      (.addContainerProperty "ID" String nil)
      (.addContainerProperty "Processes" Integer nil)
      (.addContainerProperty "CPU" Integer nil)
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
      (.addContainerProperty "Count" Integer nil)
      (.addContainerProperty "Version" String nil)
      (.addContainerProperty "Type" String nil)
      (.addContainerProperty "Status" String nil))

    ;; populate instances table
    (loop [instances instances, i 0]
      (when-let [instance (first instances)]
        (.addItem inst-table
                  (to-array
                   ;; fetch them manually and do explicit conversion where is necessary
                   ;; since changing json parser yields different number types
                   [(get instance "id")
                    (int (get instance "processes"))
                    (int (get instance "cpu"))
                    (double (get instance "memory"))
                    (get instance "agent")
                    (get instance "public-ip")
                    (get instance "private-ip")
                    (get instance "status")])
                  i)
        (recur (rest instances) (inc i))))

    ;; populate system details table
    (.addItem sys-table
              (to-array
               [(get system "name")
                (get system "region")
                (int (get system "count"))
                (get system "version")
                (get system "type")
                (get system "status")])
              0)

    (doto view
      (.setSpacing true)
      (.addComponent (doto (Label. "Rack details")
                       (.setStyleName "h2")))
      (.addComponent inst-table)
      (.addComponent sys-table))))

(defn- ^Button app-button
  "Button with some common options."
  [^String name description icon]
  (let [btn (doto (Button. name)
              (.setIcon icon))]
    (when description
      (.setDescription btn description))
    btn))

(defn- table-prop-value
  "Return value for given property/id in table."
  [^Table table id prop]
  (-> table (.getContainerProperty id prop) .getValue))

(defn- table->map
  "Convert two columns table to map, where elements from the first column will
be keys and elements from the second column values."
  [^Table table prop1 prop2]
  (loop [ids (.getItemIds table)
         ret {}]
    (if-let [id (first ids)]
      (recur (next ids) (assoc ret
                          (table-prop-value table id prop1)
                          (table-prop-value table id prop2)))
      ret)))

(defn- update-app-details
  "Fetch application details and paint table with them."
  [^VerticalLayout view node]
  (log/infof "Fetching details for '%s' application..." node)
  (let [env      (x/get-app-environment (c/read-creds) node)
        table    (Table.)
        btn-layout (HorizontalLayout.)
        add-btn  (app-button "Add" "Add new variable with value" FontAwesome/PLUS)
        edit-btn (app-button "Edit" "Edit selected variable" FontAwesome/EDIT)
        del-btn  (app-button "Delete" "Delete selected variables" FontAwesome/MINUS)
        prom-btn (app-button "Promote" "Push variables, creating new application release" FontAwesome/CLOUD_UPLOAD)
        tip      (doto (Label. (str (.getHtml FontAwesome/LIGHTBULB_O) " Variables will not be pushed untill you <i>Promote</i> changes"))
                   (.setContentMode ContentMode/HTML))
        page-len (if (>= (count env) 10)
                   10
                   0)]

    (e/with-button-event add-btn
      (ve/show-win
       (fn [var val]
         (.addItem table (to-array [(s/upper-case var) val]) nil))))

    (e/with-button-event edit-btn
      (when-let [id (.getValue table)]
        (let [k (table-prop-value table id "Variable")
              v (table-prop-value table id "Value")]
          (ve/show-win k v
                       (fn [var val]
                         (.removeItem table id)
                         (.addItem table (to-array [var val]) id))))))

    (e/with-button-event del-btn
      (when-let [id (.getValue table)]
        (.removeItem table id)))

    (e/with-button-event prom-btn
      (let [all (table->map table "Variable" "Value")]
        (let [[release-id _] (x/set-env (c/read-creds) node all)]
          (display-notification "Variables applied"
                                (format "Variables promoted to %s release." release-id)))))

    (doto table
      (.setSizeFull)
      (.setImmediate true)
      (.setPageLength page-len)
      (.setSelectable true)
      (.addContainerProperty "Variable" String nil)
      (.addContainerProperty "Value" String nil))

    (loop [env env, i 0]
      (when-let [kv (first env)]
        (.addItem table (to-array kv) i)
        (recur (rest env) (inc i))))

    (doto btn-layout
      (.setSpacing true)
      (.addComponent tip)
      (.addComponent (doto (CssLayout.)
                       (.setStyleName "v-component-group")
                       (.addComponent add-btn)
                       (.addComponent edit-btn)
                       (.addComponent del-btn)))
      (.addComponent prom-btn)
      (.setComponentAlignment tip Alignment/MIDDLE_RIGHT))

    (doto view
      (.setSpacing true)
      (.addComponent (doto (Label. (str "Variables for " node))
                       (.setStyleName "h2")))

      (.addComponent table)
      (.addComponent btn-layout)
      (.setComponentAlignment btn-layout Alignment/TOP_RIGHT))))

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

(defn- tree-expand-all
  "Expand tree."
  [^Tree tree]
  (doseq [id (.getItemIds tree)]
    (.expandItem tree id)))

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
    (tree-expand-all tree)
    ;; on every tree item click, this event handler is invoked
    (e/with-tree-item-event tree
      (let [node  (.getValue tree)
            root? (not (.getParent tree node))]
        (update-right-view! right-view node root?)))
    ;; select root to populate right view with rack details
    (.setValue tree root)
    tree))

(defn build-main-view!
  "Create main view in given window. Returns modified window object."
  [^LegacyWindow win]
  (let [layout (VerticalLayout.)
        right-view (doto (VerticalLayout.)
                     (.setStyleName "right-view"))
        toolbar (doto (HorizontalLayout.)
                  (.setStyleName "main-view-header")
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
