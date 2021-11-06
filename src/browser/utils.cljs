(ns browser.utils
  (:require [goog.string :as gstr]
            [goog.string.format]
            ["react-bootstrap" :as rb]
            ["react-selectrix" :as select]
            [clojure.string :as str]
            [react-bootstrap-icons :as icons]
            [date-fns :as d]))

(def fmt-str gstr/format)

(defn fmt-ym-date [y m] (d/format (js/Date. y m) "MMM Y"))

(defn get-category-value [year-month category]
  ;; FIXME convert into a subscription
  (cond (vector? year-month) (get category year-month)
        :else (get category :default)))

(defn get-input-string [event]
  (-> event .-target .-value))

(defn get-input-number [event]
  (-> event .-target .-value js/Number))

(defn categories-total-percentage
  ;; FIXME convert into a subscription
  "Total percentage of time allocated to all categories.
  Useful for knowing if there is still time to be distributed or not"
  ([categories] (categories-total-percentage categories nil))
  ([categories year-month]
   (->> categories vals
        (map (fn [cat]
               (:percentage (get-category-value year-month cat))))
        (apply +))))

(defn get-color-string [color]
  (gstr/format "rgba(%s, %s,  %s, %s)"
               (get color "r")
               (get color "g")
               (get color "b")
               (get color "a")))

(defn render-dot [rgba-color size & {:keys [style]}]
  [:span {:style (merge {:height size
                         :width size
                         :display "inline-block"
                         :border-radius "100%"
                         :background-color (get-color-string rgba-color)}
                        style)}])

(defn tooltip [children style]
  [:div {:style style
         :role "tooltip"
         :x-placement "bottom"
         :class "tooltip bs-tooltip-right"}
   [:div {:class "arrow"}
    [:div {:class "tooltip-inner"} children]]])

(defn format-float
  "Format float to fixed number of places, prints numbers like 5.00 as integers"
  ([number] (format-float number 2))
  ([number places]
   (let [zeros-to-remove (str "." (str/join (repeat places "0")))]
     (str/replace (.toFixed number places) zeros-to-remove ""))))

(defn percentage-string [number]
  (str (format-float number) "%"))

(defn input [label value on-change &
             {:keys [placeholder type step as rows]
              :or {as "input" rows 10}}]
  [:> rb/Form.Group {:control-id label}
   [:> rb/Form.Label label]
   [:> rb/Form.Control
    {:type type
     :as as
     :rows rows
     :defaultValue value
     :step (or step 1)
     :placeholder placeholder
     :on-change on-change}]])

(defn checkbox [label checked? on-change]
  [:> rb/Form.Group {:control-id label :class "checkbox"}
   [:> rb/Form.Label label]
   [:> rb/Form.Control
    {:type "checkbox"
     :checked checked?
     :on-change #(-> % .-target .-checked on-change) }]])

(defn input-with-btn
  [placeholder btn-text value on-change on-click
   & {:keys [btn-variant]
      :or {btn-variant "success"}} ]
  [:> rb/InputGroup
   [:> rb/FormControl {:placeholder placeholder
                       :aria-label placeholder
                       :value value
                       :on-change on-change}]
   [:> rb/InputGroup.Append
    [:> rb/Button {:variant btn-variant
                   :on-click on-click} btn-text]]])

(defn select
  "`options` is [[:option {:key key :value value} label]]"
  [label value on-change options]
  [:> rb/Form.Group {:control-id label}
   [:> rb/Form.Label label]
   [:> rb/Form.Control
    {:as "select"
     :value value
     :on-change on-change}
    options]])

(defn tags-select
  ;; https://github.com/stratos-vetsos/react-selectrix
  [label value on-change options
   & {:keys [placeholder custom-keys]}]
  [:> rb/Form.Group {:control-id label}
   [:> rb/Form.Label label]
   (js/console.log (clj->js value))
   [:div {:class "tags-select"}
    [:> select/default
     {:multiple true
      :materialize true
      :tags true
      :defaultValue (clj->js (map #(get % "key") value))
      :options options
      :placeholder placeholder
      :customKeys custom-keys
      :onChange on-change}]]])

(defn submit-btn [text on-click & {:keys [disabled variant]
                                   :or {variant "success"}}]
  [:div
   [:> rb/Button
    {:variant variant
     :on-click on-click
     :disabled disabled}
    text]])

(defn edit-btn [on-click]
  [:span {:class "ml-2 delete-btn"}
   [:> rb/Button
    {:type "button"
     :variant "secondary"
     :on-click on-click}
    [:> icons/Pencil]]])

(defn done-btn [on-click]
  [:span {:class "ml-2 delete-btn"}
   [:> rb/Button
    {:type "button"
     :variant "info"
     :on-click on-click}
    [:> icons/CheckCircle]]])

(defn delete-btn [on-click]
  [:span {:class "ml-2 delete-btn"}
   [:> rb/Button
    {:type "button"
     :variant "danger"
     :on-click on-click}
    [:> icons/Trash]]])


(defn modal [title body show? close
             & {:keys [class]}]
  [:> rb/Modal {:show show? :on-hide (fn []) :class class}
   [:> rb/Modal.Header [:div [:> rb/Modal.Title title]
                        [:button {:class "modal__close"
                                  :on-click close}
                         [:> icons/XCircle]]]]
   [:> rb/Modal.Body body]])


(defn checkmark [& {:keys [class title] :or {class ""}}]
  [:span {:title title}
   [:>  icons/Check2Circle
    {:class (str "checkmark-icon " class)}]])
