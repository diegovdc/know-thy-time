(ns browser.graphs
  (:require ["react-chartjs-2" :refer [Bar]]
            [re-frame.core :as rf]
            [browser.utils :as utils]))

(defn get-activities-data []
  (let [cats (->> @(rf/subscribe [:categories])
                  (map (fn [[cat val]] [cat (:default val)]))
                  (into {}))
        {:keys [month-free-time]} @(rf/subscribe [:free-time])
        year @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        acts (-> @(rf/subscribe [:activities])
                 (get-in [year month])
                 vals
                 (->> (mapcat vals)
                      (group-by :cat)))
        cat-names (->> cats (sort-by #(-> % second :percentage)) (map first))
        data (map (fn [cat]
                    (let [cat-hours (-> cats (get cat) :percentage
                                        (/ 100) (* month-free-time))
                          total-hours (apply + (map :time (get acts cat)))]
                      (.toFixed (* 100 (/ total-hours cat-hours)) 2)))
                  cat-names)
        background-colors (map #(-> cats (get %)
                                    :color
                                    (assoc "a" 0.3)
                                    utils/get-color-string)
                               cat-names)
        border-colors (map #(-> cats (get %)
                                :color
                                utils/get-color-string)
                           cat-names)]
    {:labels cat-names
     :datasets [{:label "Advanced %"
                 :data data
                 :backgroundColor background-colors
                 :borderColor border-colors
                 :borderWidth 1}]}))

(defn bars [title data]
  [:div {:style {:background-color "white"
                 :max-width 500
                 :width 500}}
   title
   [:> Bar {:data data
            :options {:legend {:labels {:boxWidth 0}}
                      :scales {:yAxes [{:ticks {:beginAtZero true
                                                :suggestedMax 100}}]}}}]])
