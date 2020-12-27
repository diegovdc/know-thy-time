(ns browser.views.categories
  (:require [re-frame.core :as rf]
            [browser.utils :as utils]
            [goog.string :as gstr]
            [goog.string.format]
            [reagent.core :as r]
            ["react-color" :as color]))

(defn cat-input
  ([data data-key val-name val-path]
   (cat-input data data-key val-name val-path utils/get-input-string))
  ([data data-key val-name val-path get-input-val-fn]
   (utils/input val-name (data-key data) #(rf/dispatch
                                           [:update-category
                                            val-path
                                            (get-input-val-fn %)]))))

(defn render-activity [category-name [activity-name data]]
  [:div {:key (str category-name activity-name)
         :class "categories__activity mb-2 d-flex"}
   (cat-input data :hrs activity-name
              [category-name :default :activities activity-name :hrs]
              utils/get-input-number)
   [:span {:class "align-self-center"} "hrs/month"]
   (utils/delete-btn #(rf/dispatch [:delete-category-activity
                                    [category-name :default :activities] activity-name]))])

(def new-activity (r/atom {}))
(defn create-new-activity [category-name]
  (let [new-act (get @new-activity category-name "")]
    [:div {:class "mw-300"}
     (utils/input-with-btn
      "New activity" "Create" new-act
      #(do (println (utils/get-input-string %))
           (swap! new-activity assoc category-name (utils/get-input-string %)))
      #(do (rf/dispatch [:create-category-activity
                         [category-name :default :activities new-act] {:hrs 0} ])
           (swap! new-activity dissoc category-name)))]))

(defn render-activities [category-name activities]
  [:div (map (partial render-activity category-name) activities)
   (create-new-activity category-name)]
  )

(def color-pickers (r/atom {}))
(defn toggle-color-picker [category]
  (swap! color-pickers #(assoc % category (not (get % category)))))

(defn render-category [available-hours-month [cat-name cat-data]]
  (let [data (utils/get-category-value nil cat-data)
        total-hours (/ (* (data :percentage) available-hours-month) 100)
        projected-hours (->> data :activities vals (map :hrs) (apply +))
        color ( data :color {:r (rand-int 255) :g (rand-int 255) :b (rand-int 255) :a 1})
        color-string (utils/get-color-string color)]
    [:div {:key cat-name :class "mb-4"}
     [:h2 {:class "categories__title"}
      (cat-input data
                 :percentage (str cat-name ": ")
                 [cat-name :default :percentage]
                 utils/get-input-number)
      [:span {:class "categories__percentage"} "%"]
      [:span {:on-click #(toggle-color-picker cat-name)}
       (utils/render-dot color 20)]
      (utils/delete-btn #(rf/dispatch [:delete-category cat-name]))]
     (when (@color-pickers cat-name)
       [:> color/ChromePicker {:color (or color color-string)
                               :on-change #(do
                                             (println (-> % js->clj (get "rgb") ))
                                             (rf/dispatch
                                              [:update-category
                                               [cat-name :default :color]
                                               (-> % js->clj (get "rgb") )]))}])
     [:p (gstr/format "Total hours: %s, still free hours: %s "
                 total-hours
                 (- total-hours projected-hours))]
     (render-activities cat-name (data :activities))

     ]))

(def new-category (r/atom ""))

(defn main []
  (let [categories @(rf/subscribe [:categories])
        fixed-time @(rf/subscribe [:fixed-time])
        available-hours (- 24 (apply + (vals fixed-time)))
        available-hours-month (* 30 available-hours)]
    [:div [:h1 "Categories"]
     [:div {:class "mb-3 mw-300"}
      (utils/input-with-btn
       "New category" "Create" @new-category
       #(reset! new-category (utils/get-input-string %))
       (fn []
         (rf/dispatch [:create-category @new-category])
         (reset! new-category "")))]
     [:div {:class "mb-3"}
      [:div "Total allocated time: " (utils/categories-total-percentage categories) "%"]
      [:div (gstr/format "Total available hours: %s/day, %s/month "
                    available-hours
                    available-hours-month)]]
     (doall (map (partial render-category available-hours-month) categories))]))
