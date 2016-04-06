(ns pav-conf.events
  "Event helpers and macros.")

(defmacro with-button-event
 "Macro for easier event handling on buttons."
 [button & body]
 `(.addListener ~button
   (reify com.vaadin.ui.Button$ClickListener
     (buttonClick [this event#]
       ~@body))))

(defmacro with-table-row-event
 "Macro for easier event handling on table rows."
 [table & body]
 `(.addListener ~table
   (reify com.vaadin.data.Property$ValueChangeListener
     (valueChange [this event#]
       ~@body))))

(defmacro with-table-row-double-click
  "'with-table-row-event' is more general event handling function. This one will
be called only when double click is done inside table row. You can access event object by
using 'row-event' variable.

To get clicked item, use '(.getItem row-event)'."
  [table & body]
  `(.addListener ~table
    (reify com.vaadin.event.ItemClickEvent$ItemClickListener
      (itemClick [this ^com.vaadin.event.ItemClickEvent event#]
        (if (.isDoubleClick event#)
          (let [~'row-event event#]
            ~@body))))))

(defmacro with-tree-item-event
 "Macro for easier event handling on tree items."
 [tree & body]
 `(.addListener ~tree
   (reify com.vaadin.data.Property$ValueChangeListener
     (valueChange [this event#]
       ;; make sure event value is checked as other things than
       ;; click can can fire it up
       (if (.getValue (.getProperty event#)) 
         ~@body)))))

(defmacro with-component-event
  "Get any event from selected component. To inspect what was changed, you can use 'event' generated
variable, in form:
 (.getClass event) -> e.g. Button.ClickEvent.class"
  [component & body]
  `(.addListener ~component
    (proxy [com.vaadin.ui.Component$Listener] []
      (componentEvent [^com.vaadin.ui.Component$Event event#]
        (let [~'event event#]
          ~@body)))))
