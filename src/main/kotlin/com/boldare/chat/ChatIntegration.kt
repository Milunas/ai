package com.boldare.chat

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import jakarta.inject.Singleton
import io.micronaut.http.client.HttpClient
import io.micronaut.serde.annotation.Serdeable

// https://openweathermap.org/current
@Singleton
class WeatherIntegration(
        private val client: HttpClient,
        @Value("\${weather.key}") private val weatherApiKey: String,
) {
    private val weatherLat = "44.34"
    private val weatherLon = "10.99"
    private val weatherUrl = "https://api.openweathermap.org/data/2.5/weather?lat=$weatherLat&lon=$weatherLon&appid=$weatherApiKey"
}

@Serdeable
@Introspected
data class WeatherResponse(val weather: Weather, val main: WeatherMain)

@Serdeable
@Introspected
data class Weather(val main: String, val description: String)

@Serdeable
@Introspected
data class WeatherMain(val temp: String)

@Singleton
class ChatIntegration(
        private val client: HttpClient,
        @Value("\${openai.key}") private val aiPocKey: String,
) {
    private val token = "Bearer $aiPocKey"
    private val api = "https://api.openai.com/v1"
    private val model = "gpt-3.5-turbo"

    private val chats = mutableMapOf<String, List<ChatMessage>>()

    fun openChatAndSave(): ChatResponse {
        val firstMessage = ChatMessage("user", promptMessage)
        val request = ChatRequest(model, listOf(firstMessage))
        val response = openChat(request)
        chats.putIfAbsent(response.id, listOfNotNull(
                firstMessage, response.choices.firstOrNull()?.message))
        return response
    }

    fun nextMessageAndSave(chatId: String, content: String): ChatResponse? {
        val nextMessage = ChatMessage("user", content)
        val previousMessages = chats[chatId] ?: return null
        val messagesToSend = previousMessages + nextMessage
        println("Messages to send: $messagesToSend")
        val chatRequest = ChatRequest(model, messagesToSend)
        val response = sendNext(chatRequest)
        chats[chatId] = messagesToSend.plusIfExist(response.choices.firstOrNull()?.message)
        return response
    }

    private fun openChat(chatRequest: ChatRequest): ChatResponse = client.toBlocking().retrieve(
            HttpRequest.POST("$api/chat/completions", chatRequest)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.AUTHORIZATION, token),
            ChatResponse::class.java,
    )

    private fun sendNext(chatRequest: ChatRequest): ChatResponse = client.toBlocking().retrieve(
            HttpRequest.POST("$api/chat/completions", chatRequest)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.AUTHORIZATION, token),
            ChatResponse::class.java
    )

    private fun List<ChatMessage>.plusIfExist(chatMessage: ChatMessage?) = if (chatMessage == null) this else this + chatMessage

}

@Serdeable
@Introspected
data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
)

@Serdeable
@Introspected
data class ChatMessage(
        val role: String,
        val content: String,
)

@Serdeable
@Introspected
data class ChatResponse(
        val id: String,
        val model: String,
        val choices: List<ChatChoice>,
        val usage: ChatUsage,
)

@Serdeable
@Introspected
data class ChatChoice(
        val index: Int,
        val message: ChatMessage,
        val finishReason: String?,
)

@Serdeable
@Introspected
data class ChatUsage(
        val total_tokens: Int,
)

@Controller
class Chat(private val chatIntegration: ChatIntegration) {

    @Get("/chats")
    @Produces(MediaType.APPLICATION_JSON)
    fun open() = chatIntegration.openChatAndSave()

    @Post("/chats/{id}/messages")
    @Produces(MediaType.APPLICATION_JSON)
    fun next(@PathVariable id: String, @Body content: String) =
            chatIntegration.nextMessageAndSave(id, content)
}

// gtp 4
// system message - expert
// user message
// ux/ui - show user data & weather, add reason at the end oparte na

// description from weather API, temperature from weather API
val promptMessage = "Today is clear sky. Temperature is 295.86 K." +
        // from user data storage
        "I have gas boiler as space heating type and gas boiler as water heating type. " +
        "Can you give me some tips for optimal energy usage? " +
        "There is json data that you should use. " +
        "Give me most accurate tip from provided data. " +
        "If some tip don't fit don't show it. " +
        "Start sentence with: Here is tip for you: " +
        "At the end add reason why this tip was provided. " +
        // from tips storage
        "Data: ```{\n" +
        "                \"generic-tips-thermostat\": {\n" +
        "                    \"title\": \"Small steps, big difference\",\n" +
        "                    \"content\": \"Raise or lower your thermostat by just one degree to save up to 10% of your running costs.\"\n" +
        "                },\n" +
        "                \"generic-tips-winter-sunshine\": {\n" +
        "                    \"title\": \"Warm up for free\",\n" +
        "                    \"content\": \"Let the sunshine through the north, east and west-facing windows during the day. Close curtains and blinds at night to keep this heat in.\"\n" +
        "                },\n" +
        "                \"generic-tips-summer-blinds\": {\n" +
        "                    \"title\": \"Keep the heat out\",\n" +
        "                    \"content\": \"Close curtains and shade windows during warm days to prevent the sun from heating the home.\"\n" +
        "                },\n" +
        "                \"generic-tips-standby-devices\": {\n" +
        "                    \"title\": \"Try a one switch solution\",\n" +
        "                    \"content\": \"Too time-consuming to switch off every appliance? Use a power board in a common place to shut off multiple appliances at the same time.\"\n" +
        "                },\n" +
        "                \"generic-tips-sunny-charging\": {\n" +
        "                    \"title\": \"Make the most of your solar\",\n" +
        "                    \"content\": \"If you have extra devices to charge, plug them in during the day when your solar is producing the most.\"\n" +
        "                },\n" +
        "                \"generic-tips-temp-swings\": {\n" +
        "                    \"title\": \"Take a step outside\",\n" +
        "                    \"content\": \"Watch for outdoor temperature swings throughout the week and adjust your thermostat accordingly.\"\n" +
        "                },\n" +
        "                \"generic-tips-dishwasher-wait\": {\n" +
        "                    \"title\": \"Clean dishes with the sun\",\n" +
        "                    \"content\": \"Don't need your dishes again until tomorrow? Schedule your dishwasher to run during the day to make the most of your solar.\"\n" +
        "                },\n" +
        "                \"generic-tips-reheating-food\": {\n" +
        "                    \"title\": \"Hot food, less energy\",\n" +
        "                    \"content\": \"You can save energy by using a toaster or an air fryer to reheat food instead of your oven.\"\n" +
        "                },\n" +
        "                \"generic-tips-shower-length\": {\n" +
        "                    \"title\": \"Make it a challenge\",\n" +
        "                    \"content\": \"Take a quick shower to save water heating. Losing track of time? Pick a favourite song and challenge yourself to shut off the water before it ends.\"\n" +
        "                },\n" +
        "                \"generic-tips-defrost-naturally\": {\n" +
        "                    \"title\": \"Plan ahead to save more\",\n" +
        "                    \"content\": \"Plan ahead to let frozen things thaw naturally instead of using a microwave. Chuck a note on the fridge as a reminder for tomorrow's dinner.\"\n" +
        "                },\n" +
        "                \"generic-tips-clean-lights\": {\n" +
        "                    \"title\": \"Keep it clean\",\n" +
        "                    \"content\": \"Keep lights and fittings clean. Dust on globes, shades and sensors reduces energy efficiency.\"\n" +
        "                },\n" +
        "                \"generic-tips-microwave-clock\": {\n" +
        "                    \"title\": \"Make time to turn it off\",\n" +
        "                    \"content\": \"Unplug your microwave when you’re not using it. Over a year, it can consume more energy running the clock than cooking your food!\"\n" +
        "                },\n" +
        "                \"generic-tips-batch-ironing\": {\n" +
        "                    \"title\": \"Just heat once\",\n" +
        "                    \"content\": \"Iron or steam your clothes in large batches to reduce heating up appliances every time.\"\n" +
        "                },\n" +
        "                \"generic-tips-motion-detector\": {\n" +
        "                    \"title\": \"Light only when you need it\",\n" +
        "                    \"content\": \"Don’t leave the security lights on all night or day. Have a motion detector fitted.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t10\": {\n" +
        "                    \"title\": \"Don't leave your appliances hanging\",\n" +
        "                    \"content\": \"Many appliances keep using energy when you're not using them (up to 30% of your home's energy!). Switch them off right after use to make a difference.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t20\": {\n" +
        "                    \"title\": \"Everyone needs time off\",\n" +
        "                    \"content\": \"Give your appliances a break. Unplug your appliances before you leave for a holiday.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t50\": {\n" +
        "                    \"title\": \"How hot is your water?\",\n" +
        "                    \"content\": \"Washing your dishes with cooler water can be just as effective. Try lowering the temperature settings on your dishwasher.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t60\": {\n" +
        "                    \"title\": \"Wait it out and load it up\",\n" +
        "                    \"content\": \"Being patient can save you. Wait until the machine is fully loaded before you start your dishwasher.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t90\": {\n" +
        "                    \"title\": \"Keep the warmth inside\",\n" +
        "                    \"content\": \"Use your oven lights to check if your food is ready instead of opening the door.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t110\": {\n" +
        "                    \"title\": \"Choose the right size\",\n" +
        "                    \"content\": \"Choose the right size cooking ring and use a lid to keep the heat inside your pan.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t130\": {\n" +
        "                    \"title\": \"Make the most of your pans\",\n" +
        "                    \"content\": \"Try a steamer or segmented pan for cooking your vegetables instead of using multiple rings.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t140\": {\n" +
        "                    \"title\": \"Pick the right ring\",\n" +
        "                    \"content\": \"Choose the right size ring on your stovetop to make sure you only heat the bottom of the pan.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t150\": {\n" +
        "                    \"title\": \"Do you really need that heat?\",\n" +
        "                    \"content\": \"Switch your oven off sooner - many things will keep cooking while the oven cools down.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t160\": {\n" +
        "                    \"title\": \"How much water do you need?\",\n" +
        "                    \"content\": \"When boiling vegetables, use just enough water to cover your vegetables.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t170\": {\n" +
        "                    \"title\": \"Make the most of your toast\",\n" +
        "                    \"content\": \"When making toast, using a toaster is much more efficient than using a grill.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t200\": {\n" +
        "                    \"title\": \"Wait it out and load it up\",\n" +
        "                    \"content\": \"Half loaded washing machine use costs you almost as much as a fully loaded use. Try to wait until you can fill the machine completely.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t210\": {\n" +
        "                    \"title\": \"Your clothes and bills like the cold\",\n" +
        "                    \"content\": \"Most energy for laundry is used to warm up water. Use a low temperature or eco setting that is fine tuned to work effectively.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t230\": {\n" +
        "                    \"title\": \"Make the most of your sunny day\",\n" +
        "                    \"content\": \"Check the weather forecast. Plan to run your washing machine on solar and dry your clothes outside.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t250\": {\n" +
        "                    \"title\": \"How dry is too dry?\",\n" +
        "                    \"content\": \"Only dry for as long as you need. Avoid overdrying your laundry by using automatic and timed settings on your dryer.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t270\": {\n" +
        "                    \"title\": \"Dirty filters use more energy\",\n" +
        "                    \"content\": \"A dirty filter blocks the airflow and makes your dryer work harder. Clean the filter regularly.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t280\": {\n" +
        "                    \"title\": \"Have a ball with your dryer\",\n" +
        "                    \"content\": \"Reduce your clothes drying time. Use eco balls in your dryer so warm air moves around better.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t330\": {\n" +
        "                    \"title\": \"Shed light on your light habits\",\n" +
        "                    \"content\": \"Try using lamps instead of overhead lights. Switch off the lights when you leave the room.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t340\": {\n" +
        "                    \"title\": \"Shed light on your lights\",\n" +
        "                    \"content\": \"Change to energy efficient globes. LEDs or CFLs last longer and can use up to 90% less energy compared to standard incandescent globes.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t360\": {\n" +
        "                    \"title\": \"Keep the cold inside\",\n" +
        "                    \"content\": \"Make sure to load and unload your fridge and freezer as quickly as you can.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t380\": {\n" +
        "                    \"title\": \"Don't heat up your fridge\",\n" +
        "                    \"content\": \"Let warm food cool down first before putting it directly into your fridge.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t390\": {\n" +
        "                    \"title\": \"Keep the cold inside\",\n" +
        "                    \"content\": \"Keep the cold inside. Make sure your fridge and freezer seals are air-tight.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t400\": {\n" +
        "                    \"title\": \"Unfreeze energy savings\",\n" +
        "                    \"content\": \"Opening the freezer causes frost to build up. Regularly defrost your freezer to avoid build up of ice.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t420\": {\n" +
        "                    \"title\": \"Is your food cold enough?\",\n" +
        "                    \"content\": \"Check and adjust the temperature setting of your fridge and freezer.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t470\": {\n" +
        "                    \"title\": \"Only heat what you need\",\n" +
        "                    \"content\": \"Not using this room? Avoid wasting warmth by turning off heating appliances in rooms you don't use for awhile.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t480\": {\n" +
        "                    \"title\": \"Give heat a break\",\n" +
        "                    \"content\": \"Ready for your holiday? Add turning your space heating down to your pre-trip checklist.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t490\": {\n" +
        "                    \"title\": \"Seal up energy savings\",\n" +
        "                    \"content\": \"Inspect your windows and doors to make sure there are no leaks. Door seals, draught-proofing strips and door snakes can stop air from escaping.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t510\": {\n" +
        "                    \"title\": \"Give your appliances room to breathe\",\n" +
        "                    \"content\": \"Give your heating appliances fresh air. Don't block them with curtains or furniture.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t530\": {\n" +
        "                    \"title\": \"Close up for the night\",\n" +
        "                    \"content\": \"Close your curtains at night can preserve heat when it's cold outside.\"\n" +
        "                },\n" +
        "                \"personalised-tips-t700\": {\n" +
        "                    \"title\": \"How much heat do you need?\",\n" +
        "                    \"content\": \"Lower the temperature of your warm water boiler for just-as-clean dishes and showers.\"\n" +
        "                }\n" +
        "            }```"