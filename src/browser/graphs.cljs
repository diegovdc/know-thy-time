(ns browser.graphs
  (:require ["react-chartjs-2" :refer [Bar Pie]]
            [re-frame.core :as rf]
            [browser.utils :as utils]))

(defn bars [title data & {:keys [chart-height options]}]
  [:div {:style {:background-color "white"
                 :width "100%"}}
   title
   [:> Bar {:data data
            :height chart-height
            :options (merge {:legend {:labels {:boxWidth 0}}
                             :scales {:yAxes [{:ticks {:beginAtZero true
                                                       :suggestedMax 100}}]}}
                            options)}]])

(defn pie [title data & {:keys [options]}]
  [:div {:style {:width "100%"}}
   title
   [:> Pie {:data data :options  options}]])
