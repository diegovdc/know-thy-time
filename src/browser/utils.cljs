(ns browser.utils
  (:require [goog.string :as gstr]
            [goog.string.format]
            ["react-bootstrap" :as rb]
            [clojure.string :as str]
            [react-bootstrap-icons :as icons]))

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

(defn input [label value on-change & {:keys [placeholder type step]}]
  [:> rb/Form.Group {:control-id label}
   [:> rb/Form.Label label]
   [:> rb/Form.Control
    {:type type
     :value value
     :step (or step 1)
     :placeholder placeholder
     :on-change on-change}]])

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

(defn select [label value on-change options]
  [:> rb/Form.Group {:control-id label}
   [:> rb/Form.Label label]
   [:> rb/Form.Control
    {:as "select"
     :value value
     :on-change on-change}
    options]])


(defn submit-btn [text on-click & {:keys [disabled]}]
  [:div
   [:> rb/Button
    {:variant "success"
     :on-click on-click
     :disabled disabled}
    text]])

(defn delete-btn [on-click]
  [:span {:class "ml-2 delete-btn"}
   [:> rb/Button
    {:type "button"
     :variant "danger"
     :on-click on-click}
    [:> icons/Trash]]])
