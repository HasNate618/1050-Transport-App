# **Project Overview**  

The **Hazard Tracking App** is a mobile application designed to **enhance real-time hazard reporting and visualization** in an interactive and user-friendly manner.  

The primary goal of this app is to allow users to **report and view hazardous locations** in real time on an interactive map. This can be particularly useful in situations where road obstructions, weather-related hazards, or construction zones could pose a danger to commuters.  

To achieve this, the application utilizes several technologies:  
- **HERE Maps SDK** for an interactive mapping experience  
- **JsonBin API** to store and manage user-submitted hazard data  
- **Arduino sensors** to provide real-time environmental hazard detection  

The app is designed to be intuitive, ensuring that **users can easily mark hazard locations, remove outdated reports, and view real-time updates** as new hazards are submitted.  

---

# **Key Features**  

✔ **Interactive Map Display** – Uses HERE Maps SDK to visually display hazard locations on a map.  

✔ **Crowdsourced Geodata** – Users can add report the location of hazards and people in danger to help others users avoid certain spots, and first responders to stay up to date.

✔ **Additional Drone Based Geodata** – Drones attached with the smart device can make use of sensors and computer vision to report hazards based on temperature, CO2 levels and humidity.

✔ **Data Synchronization** – The app fetches and updates hazard data dynamically from JsonBin, ensuring that users always have access to the most up-to-date information.  

✔ **Hazard Removal** – Users can mark outdated hazards as resolved, ensuring that the map remains relevant and accurate.  

---

# **Technologies Used**  

| **Component**   | **Technology** | **Purpose** |
|---------------|--------------|------------|
| **Frontend (Android App)** | Java, Android SDK | Main application interface and user interactions |
| **Mapping & Location Services** | HERE Maps SDK | Displays hazard locations on an interactive map |
| **Cloud Storage** | JsonBin API | Stores user-submitted hazard reports in JSON format |
| **Hardware Integration** | ESP32 WROVER CAM | Gathers real-world environmental hazard data |
| **Computer Vision** | Edge Impulse | Classifies and reports different hazards  |
| **Networking** | REST API (HTTP Requests) | Fetches and updates hazard data from JsonBin |

---

# **User Flow**  
The user journey consists of the following steps:  
1. **Launch the app** → Load map and fetch existing hazards from JsonBin  
2. **View hazards on the map** → Tap on a hazard for more details  
3. **Add a new hazard** → Input title, description, and location, then submit  
4. **Remove outdated hazards** → Tap and delete a hazard from the map  
5. **Arduino hazard detection** → Sensor data is sent to the app and display

&nbsp;
   
&nbsp;
![Figma Process Flow](https://i.imgur.com/5u825va.jpeg)  
