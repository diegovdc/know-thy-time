(ns browser.graphs
  (:require ["react-chartjs-2" :refer [Bar Pie Line]]
            [re-frame.core :as rf]
            [browser.utils :as utils]))

(defn bars [title data &
            {:keys [chart-height options x-title y-title]
             :or {y-title {:display true :text ""}
                  x-title {:display true :text ""}}}]
  [:div {:style {:background-color "black"
                 :width "100%"}}
   title
   [:> Bar {:data data
            :height chart-height
            :options
            (merge-with merge
                        {:plugins {:legend {:labels {:boxWidth 0}}}
                         :scales
                         {:x {:title x-title }
                          :y {:title y-title
                              :beginAtZero true
                              :ticks {:min 1
                                      :suggestedMax 100
                                      :callback (fn [value index values] value)}
                              :gridLines {:color "rgba(255,255,255, 0.05)"}}}}
                        options)}]])

(defn line [title data &
            {:keys [chart-height options y-scale-type y-max x-title y-title]
             :or {y-scale-type "logarithmic"
                  y-max 150
                  y-title {:display true :text ""}
                  x-title {:display true :text ""}}}]
  [:div {:style {:background-color "black"
                 :width "100%"}}
   title
   [:> Line {:data data
             :height chart-height
             :options (merge-with
                       merge
                       {:scales
                        {:x {:title x-title }
                         :y {:title y-title
                             :beginAtZero true
                             :ticks {:min 1
                                     :max y-max
                                     :callback (fn [value index values] value)}
                             :gridLines {:color "rgba(255,255,255, 0.05)"}
                             :type y-scale-type}}
                        ;; :plugins {:legend}
                        }
                       options)}]])

(defn pie [title data & {:keys [options]}]
  [:div {:style {:width "100%"}}
   title
   [:> Pie {:data data :options  options}]])
