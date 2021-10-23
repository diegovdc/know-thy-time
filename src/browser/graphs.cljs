(ns browser.graphs
  (:require ["react-chartjs-2" :refer [Bar Pie Line]]
            [re-frame.core :as rf]
            [browser.utils :as utils]))

(defn bars [title data & {:keys [chart-height options]}]
  [:div {:style {:background-color "black"
                 :width "100%"}}
   title
   [:> Bar {:data data
            :height chart-height
            :options (merge {:legend {:labels {:boxWidth 0}}
                             :scales {:yAxes [{:ticks {:beginAtZero true
                                                       :suggestedMax 100}
                                               :gridLines {:color "rgba(255,255,255, 0.05)"}}]}}
                            options)}]])

(defn line [title data & {:keys [chart-height options y-scale-type y-max]
                          :or {y-scale-type "logarithmic"
                               y-max 150}}]
  [:div {:style {:background-color "black"
                 :width "100%"}}
   title
   [:> Line {:data data
             :height chart-height
             :options (merge {:legend {:labels {:boxWidth 0}}
                              :scales {:yAxes [{:ticks {:beginAtZero true
                                                        :min 1
                                                        :max y-max
                                                        :callback (fn [value index values] value)}
                                                :gridLines {:color "rgba(255,255,255, 0.05)"}
                                                :type y-scale-type}]}
                              ;; :plugins {:legend}
                              }
                             options)}]])

(defn pie [title data & {:keys [options]}]
  [:div {:style {:width "100%"}}
   title
   [:> Pie {:data data :options  options}]])
