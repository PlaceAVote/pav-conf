(ns pav-conf.login-view
  "Summon authentication."
  (:import [com.vaadin.server Sizeable$Unit FontAwesome Page]
           [com.vaadin.ui LegacyWindow Label VerticalLayout HorizontalLayout Alignment Label Button CssLayout TextField PasswordField Notification]
           com.vaadin.shared.Position
           com.vaadin.ui.themes.ValoTheme)
  (:require [pav-conf.conf :as c]
            [pav-conf.events :as e]
            [clojure.tools.logging :as log]))

(defn- display-bad-login
  "Display bad login notification."
  []
  (doto (Notification. "Login failed" "Bad username or password" Notification/TYPE_WARNING_MESSAGE)
    (.setPosition Position/TOP_CENTER)
    (.show (Page/getCurrent))))

(defn- get-ip-address
  "Return ip address of currently serving user."
  []
  (-> (Page/getCurrent) .getWebBrowser .getAddress))

(defn- valid-username-password?
  "Check if username/password are correct, comparing against internal database."
  [user pass]
  (loop [db (:users (c/read-conf))]
    (when-let [[u p] (first db)]
      (if (and (= u user)
               (= p pass))
        true
        (recur (next db))))))

(defn- build-fields
  "Create login input fields."
  [on-login-success]
  (let [layout (HorizontalLayout.)
        button (doto (Button. "Sign In")
                 (.addStyleName ValoTheme/BUTTON_PRIMARY)
                 (.setClickShortcut com.vaadin.event.ShortcutAction$KeyCode/ENTER nil)
                 (.focus))
        username (doto (TextField. "Username")
                   (.setIcon FontAwesome/USER)
                   (.addStyleName ValoTheme/TEXTFIELD_INLINE_ICON))
        password (doto (PasswordField. "Password")
                   (.setIcon FontAwesome/LOCK)
                   (.addStyleName ValoTheme/TEXTFIELD_INLINE_ICON))]

    (e/with-button-event button
      (let [user (.getValue username)
            pass (.getValue password)
            ip   (get-ip-address)]
        (if-not (valid-username-password? user pass)
          (do
            (display-bad-login)
            (log/warnf "Bad login access for '%s' from IP: %s" user ip))
          (when on-login-success
            (log/infof "Successfull login for '%s' from IP: %s" user ip)
            (on-login-success)))))

    (doto layout
      (.setSpacing true)
      (.addStyleName "fields")
      (.addComponent username)
      (.addComponent password)
      (.addComponent button)
      (.setComponentAlignment button Alignment/BOTTOM_LEFT))))

(defn- build-labels
  "Labels in login form."
  []
  (doto (CssLayout.)
    (.addStyleName "labels")
    (.addComponent (doto (Label. "Login")
                     (.setSizeUndefined)
                     (.addStyleName ValoTheme/LABEL_H4)
                     (.addStyleName ValoTheme/LABEL_COLORED)))
    (.addComponent (doto (Label. "PAV Configuration")
                     (.setSizeUndefined)
                     (.addStyleName ValoTheme/LABEL_H3)
                     (.addStyleName ValoTheme/LABEL_LIGHT)))))

(defn build-login-view!
  "Create login view on given window."
  [^LegacyWindow win successfull-login-fn]
  (let [layout (VerticalLayout.)]
    (doto layout
      (.setSizeUndefined)
      (.addComponent (build-labels))
      (.addComponent (build-fields successfull-login-fn))
      (.setSpacing true)
      (.addStyleName "login-panel"))
  (.setContent win (doto (VerticalLayout.)
                     (.setSizeFull)
                     (.addComponent layout)
                     (.setComponentAlignment layout Alignment/MIDDLE_CENTER)))
  win))
