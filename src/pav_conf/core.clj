(ns pav-conf.core
  "Initialze jetty, servlet and provide facility for recompiling CSS from SCSS."
  (:gen-class)
  (:import [com.vaadin.server LegacyApplication LegacyVaadinServlet]
           [com.vaadin.ui LegacyWindow]
           com.vaadin.sass.internal.ScssStylesheet
           com.vaadin.sass.SassCompiler)
  (:require [pav-conf.jetty :as jetty]
            [pav-conf.conf :as c]
            [pav-conf.view :refer [build-main-view!]]
            [pav-conf.login-view :refer  [build-login-view!]]
            [clojure.java.io :as io]))

(defn- in-repl?
  "Determine if we are running application in dev mode (via leiningen in REPL)
or is run as jar."
  [app]
  (System/getProperty (format "%s.version" app)))

(defn- main-app
  "Initialize master window. Vaadin entry point."
  [^LegacyApplication app]
  (let [win (LegacyWindow. "PAV Configuration Manager")
        success-login #(build-main-view! win)]
    ;; do not show login in development mode, to speed up the things a bit
    (if (in-repl? "pav-conf")
      (build-main-view! win)
      (build-login-view! win success-login))
    (doto app
      (.setTheme "pav-conf")
      (.setMainWindow win))))

(def ^:private main-servlet
  (proxy [LegacyVaadinServlet] []
    (getNewApplication [request]
      (proxy [LegacyApplication] []
        (init []
          (main-app this))))))

(defn- start-app
  "Start jetty and the whole application under it."
  [conf]
  (let [port       (get conf :port 8080)
        production (-> conf :production boolean str)
        holder     (jetty/vaadin-servlet main-servlet production)]
    (jetty/start-jetty port {"/" holder})))

(defn- recompile-css
  "Force CSS recompiling. I'm not sure why, but for my case on-the-fly
recompiling doesn't work (probably due Legacy* code), so I'm going to write my own."
  []
  (print "Recompiling CSS...")
  (let [scss-path (-> "VAADIN/themes/pav-conf/styles.scss"
                      io/resource
                      io/file)
        css-path  (format "%s/styles.css" (-> scss-path .getParent str))
        scss-path (str scss-path)
        scss      (ScssStylesheet/get scss-path)]
    (.compile scss)
    (spit css-path (.printState scss))))

(defn restart-app
  "Restart application, recompiling CSS. Only for development."
  ([recompile-css?]
     (when recompile-css?
       (recompile-css))
     (pav-conf.jetty/restart-jetty))
  ([] (restart-app false)))

(defn -main
  "Main entry point."
  [& args]
  (-> (c/read-conf) start-app))
