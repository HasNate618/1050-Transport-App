#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "DHT.h"
#include "MQ135.h"
#include "secrets.h"
#include <esp32-cam-cat-dog_inferencing.h>

#define CAMERA_MODEL_WROVER_KIT // Has PSRAM

#include "img_converters.h"
#include "image_util.h"
#include "esp_camera.h"
#include "camera_pins.h"

#define DHTTYPE DHT11 // DHT 11
#define DHTPIN 13 // what pin we're connected to
// Initialize DHT sensor for normal 16mhz Arduino
DHT dht(DHTPIN, DHTTYPE);

dl_matrix3du_t *resized_matrix = NULL;
ei_impulse_result_t result = { 0 };

// JSON bin API endpoint and API key
const char PROGMEM bin_url[] = "https://api.jsonbin.io/v3/b/67e592e08a456b79667de663";
const char PROGMEM apiKey[] = "$2a$10$0wL/3asdNJyr/YV4j8IZdePui4RlgGAySCYfSwJnZK44KNoo51raO";

void setup() {
    Serial.begin(115200);

    dht.begin();

    // Camera configuration
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sccb_sda = SIOD_GPIO_NUM;
    config.pin_sccb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.frame_size = FRAMESIZE_QVGA;
    config.pixel_format = PIXFORMAT_RGB565;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.jpeg_quality = 12;
    config.fb_count = 1;

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("Camera init failed with error 0x%x\n", err);
        while (true) {}
    }

    sensor_t *s = esp_camera_sensor_get();
    if (s) {
        s->set_vflip(s, 1);
        s->set_brightness(s, 1);
        s->set_saturation(s, 0);
    }

    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    Serial.print("Connecting to WiFi...");
    while (WiFi.status() != WL_CONNECTED) {
        Serial.print(".");
        delay(500);
    }
    Serial.println("\nConnected to WiFi!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
}

String classify() {
    if (!capture()) return "Capture Error";

    signal_t signal;
    signal.total_length = EI_CLASSIFIER_INPUT_WIDTH * EI_CLASSIFIER_INPUT_HEIGHT * 3;
    signal.get_data = &raw_feature_get_data;

    EI_IMPULSE_ERROR res = run_classifier(&signal, &result, false);
    dl_matrix3du_free(resized_matrix);

    if (res != EI_IMPULSE_OK) return "Classification Error";

    int best_idx = -1;
    float best_score = 0.0;
    const float CONFIDENCE_THRESHOLD = 0.92;  // Adjust as needed

    for (size_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
      Serial.print("Label: ");
      Serial.print(result.classification[i].label);
      Serial.print(" | Score: ");
      Serial.println(result.classification[i].value, 4); // Print score with 4 decimal places

        if (result.classification[i].value > best_score) {
            best_score = result.classification[i].value;
            best_idx = i;
        }
    }

    // Return "None" if confidence is too low
    if (best_score < CONFIDENCE_THRESHOLD) return "None";

    return String(result.classification[best_idx].label);
}


bool capture() {
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("Failed to capture image");
        return false;
    }

    dl_matrix3du_t *rgb888_matrix = dl_matrix3du_alloc(1, fb->width, fb->height, 3);
    fmt2rgb888(fb->buf, fb->len, fb->format, rgb888_matrix->item);

    resized_matrix = dl_matrix3du_alloc(1, EI_CLASSIFIER_INPUT_WIDTH, EI_CLASSIFIER_INPUT_HEIGHT, 3);
    image_resize_linear(resized_matrix->item, rgb888_matrix->item, EI_CLASSIFIER_INPUT_WIDTH, EI_CLASSIFIER_INPUT_HEIGHT, 3, fb->width, fb->height);

    dl_matrix3du_free(rgb888_matrix);
    esp_camera_fb_return(fb);

    return true;
}

int raw_feature_get_data(size_t offset, size_t length, float *signal_ptr) {
    size_t pixel_ix = offset * 3;
    for (size_t i = 0; i < length; i++) {
        uint8_t r = resized_matrix->item[pixel_ix];
        uint8_t g = resized_matrix->item[pixel_ix + 1];
        uint8_t b = resized_matrix->item[pixel_ix + 2];

        signal_ptr[i] = (r << 16) + (g << 8) + b;
        pixel_ix += 3;
    }

    return 0;
}

void loop() {
  delay(10000);

  // Reading temperature or humidity takes about 250 milliseconds!
  // Sensor readings may also be up to 2 seconds 'old' (its a very slow sensor)
  float h = dht.readHumidity();
  // Read temperature as Celsius
  float t = dht.readTemperature();
  // Read temperature as Fahrenheit
  float f = dht.readTemperature(true);
  Serial.printf("Humidity: %.2f%%, Temperature: %.2fC / %.2fF\n", h, t, f);

  MQ135 gasSensor = MQ135(35);
  float gasReading = gasSensor.getPPM();
  Serial.println("MQ135 reading: " + String(gasReading) + " PPM");
  Serial.println();

  char sensorVals[64];
  sprintf(sensorVals, "\nHumidity: %.0f%%\nTemperature %.2fC\nCO2: %.2f PPM", h, t, gasReading);

  String label = classify();
  Serial.printf("Classification Result: %s\n\n", label.c_str());
  if (label=="cat") {
    addPOI("hazard", "BIG CAT", sensorVals, 43.0075, -81.2763);
  } else if (label=="dog") {
    addPOI("hazard", "BIG DOG", sensorVals, 43.0075, -81.2763);
  }

}

// Function to add a POI to the JSON bin, avoiding duplicates
void addPOI(String type, String title, String description, float lat, float lon) {
  Serial.println("Checking if POI already exists...");

  // Make an HTTP GET request to fetch the existing data from the JSON bin
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(bin_url);
    http.addHeader("X-Master-Key", apiKey);
    
    int httpResponseCode = http.GET();

    if (httpResponseCode > 0) {
      String response = http.getString();
      Serial.println("Fetched JSON data: " + response);

      // Parse the JSON data
      DynamicJsonDocument doc(2048);
      DeserializationError error = deserializeJson(doc, response);

      if (error) {
        Serial.println("Error parsing JSON: " + String(error.f_str()));
        return;
      }

      JsonArray points = doc["record"]["points"].as<JsonArray>();

      // Check if POI already exists
      for (JsonObject poi : points) {
        float existingLat = poi["coordinates"]["latitude"];
        float existingLon = poi["coordinates"]["longitude"];

        if (abs(existingLat - lat) < 0.0001 && abs(existingLon - lon) < 0.0001) {
          Serial.println("POI already exists. Not adding.");
          return;
        }
      }

      // If POI doesn't exist, add it
      Serial.println("Adding new POI...");
      JsonObject newPoi = points.createNestedObject();
      newPoi["type"] = type;
      newPoi["title"] = title;
      newPoi["description"] = description;
      newPoi["coordinates"]["latitude"] = lat;
      newPoi["coordinates"]["longitude"] = lon;
      newPoi["userSubmitted"] = false;

      // Prepare the updated JSON
      String updatedJson;
      serializeJson(doc["record"], updatedJson);

      // Send updated data with a PUT request
      http.begin(bin_url);
      http.addHeader("Content-Type", "application/json");
      http.addHeader("X-Master-Key", apiKey);

      httpResponseCode = http.PUT(updatedJson);

      if (httpResponseCode > 0) {
        Serial.println("POI added successfully!");
      } else {
        Serial.println("Error updating JSON bin: " + String(httpResponseCode));
      }
    } else {
      Serial.println("Error fetching data: " + String(httpResponseCode));
    }

    http.end();
  } else {
    Serial.println("WiFi not connected");
  }
}
