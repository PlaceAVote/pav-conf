(ns pav-conf.var-editor
  "Window editing facility for variable."
  (:import [com.vaadin.ui Window HorizontalLayout VerticalLayout Button TextField Alignment]
           com.vaadin.ui.UI
           com.vaadin.ui.themes.ValoTheme)
  (:require [pav-conf.events :as e]
            [clojure.string :as s]))

(defn show-win
  "Display editor for given key/value combos. If they are nil,
assume adding action; otherwise editing."
  ([k v save-fn]
     (let [win (Window. (if k
                          "Edit variable"
                          "Add new variable"))
           kfield (TextField. "Key")
           vfield (TextField. "Value")
           layout (VerticalLayout.)
           hlayout (HorizontalLayout.)
           save   (doto (Button. "Save")
                    (.addStyleName ValoTheme/BUTTON_PRIMARY)
                    (.setClickShortcut com.vaadin.event.ShortcutAction$KeyCode/ENTER nil))]

       (when save-fn
         (e/with-button-event save
           (let [key (.getValue kfield)
                 val (.getValue vfield)]
             ;; prevent closing dialog if user didn't enter anything
             (when-not (or (s/blank? key)
                           (s/blank? val))
               (.. (UI/getCurrent) (removeWindow win))
               (save-fn (.getValue kfield) (.getValue vfield))))))

       (when k 
         (.setValue kfield k)
         (.setEnabled kfield false))

       (when v (.setValue vfield v))
       
       (doto hlayout
         (.setSpacing true)
         (.setSizeFull)
         (.addComponent kfield)
         (.addComponent vfield)
         (.setExpandRatio vfield 1))
       
       (doto layout
         (.addComponent hlayout)
         (.addComponent save)
         (.setComponentAlignment save Alignment/TOP_RIGHT)
         (.setMargin true)
         (.setSpacing true)
         (.setSizeUndefined))

       (doto win
         (.setContent layout)
         (.center))
       (.. (UI/getCurrent) (addWindow win))))
  ([save-fn] (show-win nil nil save-fn)))
