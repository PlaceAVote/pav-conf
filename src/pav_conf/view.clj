(ns pav-conf.view
  "Main view with actions."
  (:import [com.vaadin.server Sizeable$Unit FontAwesome Page]
           [com.vaadin.ui LegacyWindow Label VerticalLayout HorizontalLayout Alignment HorizontalSplitPanel
            Tree Label Table Button CssLayout Notification AbstractOrderedLayout]
           com.vaadin.shared.ui.label.ContentMode
           com.vaadin.ui.themes.ValoTheme)
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
    (.setStyleName "dark closable")
    (.show (Page/getCurrent))))

(defn- display-error-on-component
  "Add error label on given component."
  [^AbstractOrderedLayout c ^String message]
  (.addComponent c (doto (Label. message)
                     (.addStyleName ValoTheme/LABEL_FAILURE))))

(defn- make-standard-table!
  "Take table object and return table with standard options, filling with
vector of properties with corresponding type."
  [^Table table props]
  (let [table (doto table
                (.setSizeFull)
                (.setSelectable true)
                (.setPageLength 0))]
    (doseq [[name klass] props]
      (.addContainerProperty table name klass nil))
    table))

(defn- group-buttons
  "Take buttons and group them using CssLayout."
  [buttons]
  (let [layout (doto (CssLayout.)
                 (.setStyleName "v-component-group"))]
    (doseq [b buttons]
      (.addComponent layout b))
    layout))

;;; right view

(defn- update-rack-details-with-data
  "Fetch rack details using provided data."
  [^VerticalLayout view rack instances system]
  (let [creds      (c/read-creds rack)
        instances  (x/get-instances creds)
        system     (x/get-system creds)
        inst-table (Table. "Instances")
        sys-table  (Table. "System details")]

    (make-standard-table! inst-table [["ID" String] ["Processes" Integer] ["CPU" Integer]
                                      ["Memory" Double] ["Agent" Boolean] ["Public IP" String]
                                      ["Private IP" String] ["Status" String]])

    (make-standard-table! sys-table [["Name" String] ["Region" String] ["Count" Integer]
                                     ["Version" String] ["Type" String] ["Status" String]])

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
      (.addComponent (doto (Label. (format "Rack details for %s" rack))
                       (.setStyleName "h2")))
      (.addComponent inst-table)
      (.addComponent sys-table))))

(defn- update-rack-details
  "Fetch rack details and paint table with them."
  [^VerticalLayout view rack]
  (log/infof "Fetching rack details for %s..." rack)
  (try
    (let [creds     (c/read-creds rack)
          instances (x/get-instances creds)
          system    (x/get-system creds)]
      (update-rack-details-with-data view rack instances system))
    (catch Exception e
      (log/errorf e "Failed to get rack details from %s" rack)
      (let [info (ex-data e)]
        (display-error-on-component view (format "Error %s (%s)"
                                                 (:status info)
                                                 (:body info)))))))

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

(defn- update-app-details-with-data
  "Fetch application details using provided environment variables."
  [^VerticalLayout view creds node env]
  (let [table      (Table.)
        btn-layout (HorizontalLayout.)
        add-btn    (app-button "Add" "Add new variable with value" FontAwesome/PLUS)
        edit-btn   (app-button "Edit" "Edit selected variable" FontAwesome/EDIT)
        del-btn    (app-button "Delete" "Delete selected variables" FontAwesome/MINUS)
        prom-btn   (app-button "Promote" "Push variables, creating new application release" FontAwesome/CLOUD_UPLOAD)
        import-btn (app-button "Import" "Import variables from file" FontAwesome/UPLOAD)
        export-btn (app-button "Export" "Export all current variables to file" FontAwesome/DOWNLOAD)
        tip        (doto (Label. (str (.getHtml FontAwesome/LIGHTBULB_O) " Variables will not be pushed untill you <i>Promote</i> changes"))
                     (.setContentMode ContentMode/HTML))
        page-len   (if (>= (count env) 10) 10 0)]

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
        (let [[release-id _] (x/set-env creds node all)]
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
      (.addComponent (group-buttons [import-btn export-btn]))
      (.addComponent (group-buttons [add-btn edit-btn del-btn]))
      (.addComponent prom-btn))

    (doto view
      (.setSpacing true)
      (.addComponent (doto (Label. (str "Variables for " node))
                       (.setStyleName "h2")))
      (.addComponent tip)
      (.addComponent table)
      (.addComponent btn-layout)
      (.setComponentAlignment btn-layout Alignment/TOP_RIGHT))))

(defn- update-app-details
  "Fetch application details and paint table with them."
  [^VerticalLayout view rack node]
  (log/infof "Fetching details for '%s (rack: %s)' application..." node rack)
  (try
    (let [creds (c/read-creds rack)
          env   (x/get-app-environment creds node)]
      (update-app-details-with-data view creds node env))
    (catch Exception e
      (log/errorf e "Failed to get application details for %s (rack: %s)" node rack)
      (let [info (ex-data e)]
        (display-error-on-component view (format "Error %s (%s)"
                                                 (:status info)
                                                 (:body info)))))))

(defn- update-right-view!
  "Repaint right view with table and other details, depending on tree node.
If root is selected, that means user clicked on rack name and we will fetch all rack server instances, painting
specific table. If any of nodes is selected, that means user clicked specific application, fetching
necessary details, painting own table."
  [^Tree tree ^VerticalLayout view id]
  (.removeAllComponents view)
  (let [caption (.getItemCaption tree id)
        root    (.getParent tree id)]
  ;; Here is how naming scheme works and how is accessed. Vaadin Tree class support adding only unique ID's
  ;; and when is added another same one, nodes from the old one are overwritten. Because of that, Tree root
  ;; nodes are labeled as ID (Vaadin for Tree node first display caption, then ID as label) and childs are
  ;; having unique ID since racks can have the same applications. Child labels are stored as captions.
  (if-not root
    (update-rack-details view id)
    (update-app-details view root caption))))

;;; left tree

(defn- push-node!
  "Append node on given tree, with given root."
  [^Tree tree ^String root ^String name icon]
  ;; rely on this, instead of (.addItem tree name) since we can have
  ;; multiple entries with the same name, which Tree forbids when the name is used as ID
  (let [unique-id (.addItem tree)]
    (doto tree
      (.setItemCaption unique-id name)
      (.setParent unique-id root)
      (.setChildrenAllowed unique-id false)
      (.setItemIcon unique-id icon))))

(defn- tree-expand-all
  "Expand tree."
  [^Tree tree]
  (doseq [id (.getItemIds tree)]
    (.expandItem tree id)))

(defn- add-rack-to-tree!
  "Fill tree with new rack."
  [^Tree tree rack]
  (.addItem tree rack)
  (when-let [data (try
                    (-> rack c/read-creds x/get-apps)
                    (catch Exception e
                      (log/errorf e "Failed to fetch details for '%s' rack" rack)))]
    (doseq [item data]
      (push-node! tree rack (get item "name" "<unknown>") (if (x/running? item)
                                                            FontAwesome/CHECK
                                                            FontAwesome/CLOSE)))))

(defn- build-apps-tree
  "Create applications tree, filling it with all available racks."
  [^VerticalLayout right-view]
  (let [tree  (Tree.)
        racks (c/all-convox-racks)]

    (doseq [r racks]
      (add-rack-to-tree! tree r))

    (tree-expand-all tree)

    ;; on every tree item click, this event handler is invoked
    (e/with-tree-item-event tree
      (when-let [id (.getValue tree)]
        (update-right-view! tree right-view id)))
    tree))

(defn build-main-view!
  "Create main view in given window. Returns modified window object."
  [^LegacyWindow win]
  (let [layout (VerticalLayout.)
        right-view (doto (VerticalLayout.)
                     (.setStyleName "right-view")
                     (.addComponent (Label.
                                     (str "Manager fully loaded. Choose one of the hosts on left pane to "
                                          "manage configurations or select rack name to access rack details."))))
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
