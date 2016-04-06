(ns pav-conf.jetty
  "Jetty code for starting background jetty server with given servlet holder."
  (:gen-class)
  (:import org.mortbay.jetty.Server
           javax.servlet.Servlet
           [org.mortbay.jetty.servlet ServletHolder Context DefaultServlet]
           [org.mortbay.jetty.webapp WebAppContext]))

(defonce ^:dynamic *jetty-global* (ref nil))

(defn servlet-holder
  "Create ServletHolder with populated map of values. Stolen from compojure."
  [^Servlet servlet params]
  (let [holder (new ServletHolder servlet)]
    (doseq [[key val] params]
      (.setInitParameter holder (name key) (str val)))
    holder))

(defn static-content
  "Create ServletHolder meant for serving static files. Path component should present
folder path from where content will be served."
  [path production-mode]
  (servlet-holder org.mortbay.jetty.servlet.DefaultServlet
                  {:resourceBase path
                   :dirAllowed "true"
                   :productionMode production-mode}))

(defn add-servlet!
  "Add servlet to jetty server instance."
  [^Server server servlet path context-path]
  (let [holder (if (instance? ServletHolder servlet)
                 servlet
                 (new ServletHolder ^Servlet servlet))
        context (new Context server context-path Context/SESSIONS)]
    (.addServlet ^Context context ^ServletHolder holder ^String path)
    (.addHandler server context)))

(defn start-jetty
  "Start jetty at given port with mapped routes. Because jetty understainds context and paths
context is url after root domain and path is everything after context,  the following mapping is
understaind as routes:
  {'/foo' holder  -> 'foo' context for 'holder' servlet
   '/foo' ['/*' holder] -> same as above
   '/baz' ['/a1' holder1 '/a2' holder2] -> two links under 'baz' context with different servlets}"
  [port routes]
  (if-not (map? routes) (throw "Routes must be in a hash map form"))
  (let [server (new Server port)]
    (doseq [[context servlet] routes]
      (let [r (partition 2
                (if (or (vector? servlet) (list? servlet))
                  servlet
                  ["/*" servlet]))]
        (doseq [[route ser] r]
          (add-servlet! server ser route context))))
    ;; start it in the background
    (.start server)
    (dosync (ref-set *jetty-global* server))
    server))

(defn stop-jetty
  "Stop jetty instance."
  ([^Server jetty]
   (when jetty
     (.stop jetty)))
  ([] (stop-jetty @*jetty-global*)))

(defn restart-jetty
  "Restart jetty instance."
  ([^Server jetty]
   (when jetty
     (.stop jetty)
     (.start jetty)))
  ([] (restart-jetty @*jetty-global*)))

(defn vaadin-servlet
  "Helper for easier creating Vaadin servlets. Basically, this function will call 'servlet-holder' but also
explicitly set application class string, from Clojure proxy object. This class name is used by Vaadin internally
to find out main entry point."
  ([servlet production widgetset]
     (servlet-holder servlet
                     (merge
                      {;; small trick to extract classname as string, since (str (class obj)) will return 'class ClassName'
                       ;; and we are interested only in secoond part
                       :application (-> servlet class str (.split " ") second)
                       :productionMode production}
                      (if widgetset
                        {:widgetset widgetset}))))
  ([servlet production] (vaadin-servlet servlet production nil)))
