(ns browser.alert
  (:require ["react-bootstrap" :as rb]
            [re-frame.core :as rf]))

(defn main []
  (let [{:keys [msg variant]} @(rf/subscribe [:alert])]
    (when msg [:div {:class "alert__container"}
               [:> rb/Alert
                {:variant variant
                 :dismissible true
                 :on-close #(rf/dispatch [:close-alert])}
                msg]])))
