(ns browser.day-qualities.modal
  (:require [browser.db :as db]
            [browser.utils :as utils :refer [format-float get-category-value]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.set :as set]
            [browser.day-qualities.state :refer [is-valid? is-incomplete?]]))

(defonce modal-state (r/atom {}))

(defn open-modal [year month day day-name]
  (reset! modal-state
          @(rf/subscribe [:day-qualities/get-day
                          year month day day-name])))

(defn close-modal [] (reset! modal-state {}))

(rf/reg-fx :day-qualities-modal/close close-modal)


(defn set-val [f k v] (swap! modal-state assoc k (f v)))

(defn- numerical-options [min* max*]
  (concat [[:option {:key :default :value ""} "Select a level"]]
          (map (fn [n] [:option {:key n :value n} n])
               (range min* max*))))

(defn modal []
  (let [{:keys [day-name energy-level
                mood-level
                productivity-level
                creativity-level
                stress-level
                states-of-being
                sleep-hours
                sleep-quality
                notes]} @modal-state
        states-of-being-options (sort-by :name
                                         @(rf/subscribe [:states-of-being]))]
    (utils/modal
     (utils/fmt-str "Day qualities for: %s" (or day-name ""))
     [:div {:class "day-qualities-modal__form"}
      [:div {:class "day-qualities-modal__col-1"}
       (utils/select "Energy Level (low to high)" energy-level
                     #(set-val utils/get-input-number :energy-level %)
                     (numerical-options 1 6))
       (utils/select "Productivity Level (low to high)" productivity-level
                     #(set-val utils/get-input-number :productivity-level %)
                     (numerical-options 1 6))
       (utils/select "Creativity Level (low to high)" creativity-level
                     #(set-val utils/get-input-number :creativity-level %)
                     (numerical-options 1 6))
       (utils/select "Stress Level (none to high)" stress-level
                     #(set-val utils/get-input-number :stress-level %)
                     (numerical-options 1 6))
       (utils/select "Mood Level (bad to great)" mood-level
                     #(set-val utils/get-input-number :mood-level %)
                     (numerical-options 1 6))
       (utils/select "Sleep hours (day before)" sleep-hours
                     #(set-val utils/get-input-number :sleep-hours %)
                     (numerical-options 1 12))
       (utils/select "Sleep quality (bad to great, day before)" sleep-quality
                     #(set-val utils/get-input-number :sleep-quality %)
                     (numerical-options 1 6))
       (utils/tags-select "Emotions and other states of being"
                          (map #(set/rename-keys % {:id "key" :name "label"})
                               states-of-being)
                          #(set-val js->clj :states-of-being %)
                          (map #(set/rename-keys % {:id "key" :name "label"})
                               states-of-being-options)
                          :placeholder "  Type something")]
      [:div {:class "day-qualities-modal__col-2"}
       (utils/input "Notes" notes
                    #(set-val utils/get-input-string :notes %)
                    :as "textarea")
       [:p {:class "text-center"}
        (utils/submit-btn
         "Save"
         #(rf/dispatch [:day-qualities/create-day @modal-state]))]]]
     (not (nil? (@modal-state :day)))
     close-modal
     :class "day-qualities-modal")))


(defn status-checkmark [year month day]
  (let [day-quality (get-in @(rf/subscribe [:day-qualities/get-all])
                            [year month day])]
    (cond (is-valid? day-quality)
          (utils/checkmark :class "checkmark-icon--success")

          (is-incomplete? day-quality)
          (utils/checkmark
           :class "checkmark-icon--warning"
           :title "Some qualities are missing, please finish completing them."))))

(defn button [year month day day-name]
  [:div {:class "d-flex"}
   (utils/submit-btn
    "Day Qualities"
    (fn [] (open-modal year month day day-name)))
   (status-checkmark year month day)])
