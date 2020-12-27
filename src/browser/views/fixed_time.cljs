(ns browser.views.fixed-time
  (:require [reagent.core :as r]
            [browser.utils :as utils]
            [re-frame.core :as rf]
            [goog.string :as gstr]))
(def new-category (r/atom ""))

(defn render-category [[fixed-cat time]]
  [:h2 {:key fixed-cat :class "categories__title categories__title--large"}
   (utils/input fixed-cat time
                #(rf/dispatch [:update-fixed-time
                               fixed-cat (utils/get-input-number %)])
                :type "number"
                :step 0.25)
   [:span {:class "categories__percentage"} "hrs"]
   (utils/delete-btn #(rf/dispatch [:delete-fixed-time fixed-cat]))])


(defn main []
  (let [fixed-times @(rf/subscribe [:fixed-time])
        total (apply + (vals fixed-times))]
    [:div [:h1 (gstr/format "Fixed Time (%s hrs)" total)]
     [:div {:class "mb-3 mw-300"}
      (utils/input-with-btn
       "New category" "Create" @new-category
       #(reset! new-category (utils/get-input-string %))
       (fn []
         (rf/dispatch [:create-fixed-time @new-category])
         (reset! new-category "")))
      ]
     #_[:div {:class "mb-3"}
        [:div "Total allocated time: " (utils/categories-total-percentage categories) "%"]
        [:div (format "Total available hours: %s/day, %s/month "
                      available-hours
                      available-hours-month)]]
     (doall (map (partial render-category) fixed-times))]))
