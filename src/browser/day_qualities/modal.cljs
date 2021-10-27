(ns browser.day-qualities.modal
  (:require [browser.db :as db]
            [browser.utils :as utils :refer [format-float get-category-value]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.set :as set]
            [browser.day-qualities.state :refer [is-valid?]]))

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

(defn day-quality-modal []
  (let [{:keys [day-name energy-level
                mood-level
                productivity-level
                creativity-level
                stress-level
                states-of-being
                notes]} @modal-state
        states-of-being-options @(rf/subscribe [:states-of-being])]
    ;; TODO (left of here) need to create the daily-quality and create the new states of being
    (utils/modal
     (utils/fmt-str "Day qualities for: %s" (or day-name ""))
     [:div {:class "form-width"}
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
      (utils/tags-select "Emotions and other states of being"
                         (map #(set/rename-keys % {:id "key" :name "label"})
                              states-of-being)
                         #(set-val js->clj :states-of-being %)
                         (map #(set/rename-keys % {:id "key" :name "label"})
                              states-of-being-options)
                         :placeholder "  Type something")
      (utils/input "Notes" notes
                   #(set-val utils/get-input-string :notes %)
                   :as "textarea")
      (utils/submit-btn "Save"
                        #(rf/dispatch [:day-qualities/create-day @modal-state])
                        ;; :disabled (not (is-valid? @modal-state))
                        )]
     (not (nil? (@modal-state :day)))
     close-modal)))
