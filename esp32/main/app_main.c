#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "driver/gpio.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "esp_system.h"
#include "esp_tls.h"
#include "mqtt_client.h"
#include "nvs_flash.h"
#include "protocol_examples_common.h"

static const char* TAG = "MQTTS_EXAMPLE";
#define TRIG 17
#define ECHO 16
#define LED 2
#define ECHO_MASK (1ULL << ECHO)
#define STACK_SIZE 2048

esp_mqtt_client_handle_t client;

//超声波测距的Task
void ultraSonic(void* pvParameters) {
    char buf[20];
    while (1) {
        //TRIG拉高一下
        gpio_set_level(TRIG, 1);
        vTaskDelay(10 / portTICK_PERIOD_MS);
        gpio_set_level(TRIG, 0);
        //ECHO计时
        while (gpio_get_level(ECHO) == 0)
            ;
        int64_t begin = esp_timer_get_time();
        gpio_set_level(LED, 1);
        while (gpio_get_level(ECHO) == 1)
            ;
        int64_t end = esp_timer_get_time();
        gpio_set_level(LED, 0);
        //计算距离
        double dis = (end - begin) / 1000.0 * 17;
        //测距信息串口输出
        printf("%lld %lld %lld %lf\n", begin, end, end - begin, dis);
        //测距信息通过mqtts发布
        sprintf(buf, "%f", dis);
        int msg_id =
            esp_mqtt_client_publish(client, "/esp32/pub", buf, 0, 0, 0);
        ESP_LOGI(TAG, "sent distance successful, msg_id=%d", msg_id);

        fflush(stdout);
        //延时2秒
        vTaskDelay(2000 / portTICK_PERIOD_MS);
    }
}

//我也不知道这是干啥的
static void send_binary(esp_mqtt_client_handle_t client) {
    spi_flash_mmap_handle_t out_handle;
    const void* binary_address;
    const esp_partition_t* partition = esp_ota_get_running_partition();
    esp_partition_mmap(partition, 0, partition->size, SPI_FLASH_MMAP_DATA,
                       &binary_address, &out_handle);
    int msg_id = esp_mqtt_client_publish(client, "/topic/binary",
                                         binary_address, partition->size, 0, 0);
    ESP_LOGI(TAG, "binary sent with msg_id=%d", msg_id);
}

//mqtt事件的回调函数
static esp_err_t mqtt_event_handler_cb(esp_mqtt_event_handle_t event) {
    esp_mqtt_client_handle_t client = event->client;
    int msg_id;
    // your_context_t *context = event->context;
    switch (event->event_id) {
        //连接上时
        case MQTT_EVENT_CONNECTED:
            ESP_LOGI(TAG, "MQTT_EVENT_CONNECTED");
            //在/esp32/pub主题下发送"hello"
            msg_id =
                esp_mqtt_client_publish(client, "/esp32/pub", "hello", 0, 0, 0);
            ESP_LOGI(TAG, "sent hello successful, msg_id=%d", msg_id);
            //订阅/esp32/sub主题
            msg_id = esp_mqtt_client_subscribe(client, "/esp32/sub", 1);
            ESP_LOGI(TAG, "sent subscribe successful, msg_id=%d", msg_id);
            //创建和启动超声波测距Task
            TaskHandle_t xHandle = NULL;
            xTaskCreate(ultraSonic, "distance_task", STACK_SIZE, NULL,
                        tskIDLE_PRIORITY, &xHandle);
            configASSERT(xHandle);
            break;
        //断开连接时，记个日志
        case MQTT_EVENT_DISCONNECTED:
            ESP_LOGI(TAG, "MQTT_EVENT_DISCONNECTED");
            break;
        //订阅时，记个日志
        case MQTT_EVENT_SUBSCRIBED:
            ESP_LOGI(TAG, "MQTT_EVENT_SUBSCRIBED, msg_id=%d", event->msg_id);
            break;
        //取消订阅时，记个日志
        case MQTT_EVENT_UNSUBSCRIBED:
            ESP_LOGI(TAG, "MQTT_EVENT_UNSUBSCRIBED, msg_id=%d", event->msg_id);
            break;
        //发布信息时，记个日志
        case MQTT_EVENT_PUBLISHED:
            ESP_LOGI(TAG, "MQTT_EVENT_PUBLISHED, msg_id=%d", event->msg_id);
            break;
        //接收到消息时
        case MQTT_EVENT_DATA:
            ESP_LOGI(TAG, "MQTT_EVENT_DATA");
            //串口输出消息的主题和内容
            printf("TOPIC=%.*s\r\n", event->topic_len, event->topic);
            printf("DATA=%.*s\r\n", event->data_len, event->data);
            //下面这个调用了上面那个我也不知道干啥的函数，总之就是不管他
            if (strncmp(event->data, "send binary please", event->data_len) ==
                0) {
                ESP_LOGI(TAG, "Sending the binary");
                send_binary(client);
            }
            //在/esp32/reply主题发布"message got"信息
            msg_id = esp_mqtt_client_publish(client, "/esp32/reply",
                                             "message got", 0, 0, 0);
            ESP_LOGI(TAG, "sent reply successful, msg_id=%d", msg_id);
            break;
        //发生错误时，记一些日志
        case MQTT_EVENT_ERROR:
            ESP_LOGI(TAG, "MQTT_EVENT_ERROR");
            if (event->error_handle->error_type == MQTT_ERROR_TYPE_ESP_TLS) {
                ESP_LOGI(TAG, "Last error code reported from esp-tls: 0x%x",
                         event->error_handle->esp_tls_last_esp_err);
                ESP_LOGI(TAG, "Last tls stack error number: 0x%x",
                         event->error_handle->esp_tls_stack_err);
            } else if (event->error_handle->error_type ==
                       MQTT_ERROR_TYPE_CONNECTION_REFUSED) {
                ESP_LOGI(TAG, "Connection refused error: 0x%x",
                         event->error_handle->connect_return_code);
            } else {
                ESP_LOGW(TAG, "Unknown error type: 0x%x",
                         event->error_handle->error_type);
            }
            break;
        default:
            ESP_LOGI(TAG, "Other event id:%d", event->event_id);
            break;
    }
    return ESP_OK;
}

//大概是和回调函数有关的东西
static void mqtt_event_handler(void* handler_args,
                               esp_event_base_t base,
                               int32_t event_id,
                               void* event_data) {
    ESP_LOGD(TAG, "Event dispatched from event loop base=%s, event_id=%d", base,
             event_id);
    mqtt_event_handler_cb(event_data);
}

static void mqtt_app_start(void) {
    //mqtt客户端的配置，包括服务器URI，CA证书，用户名密码，这里URI和CA等的内容隐去了
    const esp_mqtt_client_config_t mqtt_cfg = {
        .uri = "mqtts://ip:port",
        .cert_pem = (const char *)"CA",
        .username = "",
        .password = "",
    };

    ESP_LOGI(TAG, "[APP] Free memory: %d bytes", esp_get_free_heap_size());
    //用配置生成客户端
    client = esp_mqtt_client_init(&mqtt_cfg);
    //下面绑定回调函数，启动客户端
    esp_mqtt_client_register_event(client, ESP_EVENT_ANY_ID, mqtt_event_handler,
                                   client);
    esp_mqtt_client_start(client);
}

//程序从这里开始运行
void app_main(void) {
    //设置GPIO模式，准备驱动超声波模块
    gpio_set_direction(TRIG, GPIO_MODE_OUTPUT);
    gpio_set_direction(ECHO, GPIO_MODE_INPUT);
    gpio_set_direction(LED, GPIO_MODE_OUTPUT);
    //一些日志相关的东西
    ESP_LOGI(TAG, "[APP] Startup..");
    ESP_LOGI(TAG, "[APP] Free memory: %d bytes", esp_get_free_heap_size());
    ESP_LOGI(TAG, "[APP] IDF version: %s", esp_get_idf_version());
    esp_log_level_set("*", ESP_LOG_INFO);
    esp_log_level_set("esp-tls", ESP_LOG_VERBOSE);
    esp_log_level_set("MQTT_CLIENT", ESP_LOG_VERBOSE);
    esp_log_level_set("MQTT_EXAMPLE", ESP_LOG_VERBOSE);
    esp_log_level_set("TRANSPORT_TCP", ESP_LOG_VERBOSE);
    esp_log_level_set("TRANSPORT_SSL", ESP_LOG_VERBOSE);
    esp_log_level_set("TRANSPORT", ESP_LOG_VERBOSE);
    esp_log_level_set("OUTBOX", ESP_LOG_VERBOSE);
    //一些联网相关的东西
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    ESP_ERROR_CHECK(example_connect());
    //启动mqtt相关的东西
    mqtt_app_start();
}
