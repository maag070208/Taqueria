import React from "react";
import ReactDOM from "react-dom/client";
import { ITThemeProvider } from "@axzydev/axzy_ui_system";
import App from "./App";
import "./index.css";

/** La landing es clara por diseño de marca: se fija antes de montar la librería. */
if (!localStorage.getItem("it-theme-dark-mode")) localStorage.setItem("it-theme-dark-mode", "light");

const brandTheme = {
  primary: "#3e8e6c",
};

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ITThemeProvider theme={brandTheme} showFab={false}>
      <App />
    </ITThemeProvider>
  </React.StrictMode>,
);
