(ns browser.views.main
  (:require [browser.budget :as budget]
            [browser.month :as month]))

(defn main
  []
  [:div {:class "main-view__container"}
   [:div
    [:div {:class "main-view__month"} [month/main]]]
   [:div
    [:div {:style {}}]
    [:div {:class "main-view__budget"} (budget/main)]]])
