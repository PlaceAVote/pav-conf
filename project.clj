(defproject pav-conf "0.1.0-SNAPSHOT"
  :description "Application for easier configuration of Convox environment variables"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "2.1.0"]
                 [cheshire "5.5.0"]
                 [com.vaadin/vaadin-server "7.6.4"]
                 [com.vaadin/vaadin-client-compiled "7.6.4"]
                 [com.vaadin/vaadin-themes "7.6.4"]
				 [org.mortbay.jetty/jetty "6.1.25"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]]
  :profiles {:jvm-opts ^:replace ["-Xms256m" "-Xmx512m" "-Xss512k" "-XX:MaxMetaspaceSize=150m"]
             :uberjar {:aot :all
                       :uberjar-name "pav-conf.jar"}}
  :global-vars {*warn-on-reflection* true}
  :repl-options {:port 7888}
  :omit-source true
  :main pav-conf.core)

 
