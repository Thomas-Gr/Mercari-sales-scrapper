package freee

import common.MercariData
import common.Scrapper
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import common.DRIVER
import common.FREEE_COMPANY_ID
import common.FREEE_TOKEN
import java.io.File
import java.io.FileReader
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.Route

fun main() {
  Main().run()
}

class Main {
  val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
  val factory = JsonFactory()
  val mapper = ObjectMapper(factory)
  val scrapper = Scrapper(DRIVER)

  val START_DATE = "2021-11-13"
  val httpClient = createHttpClient()

  private val JAPANESE_TIME_FORMATTER = DateTimeFormatter.ofPattern("YYYY年M月D日 H:mm")
  private val PAYPAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("M/D/YYYY")
  private val FREEE_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-DD")

  data class PayPalData(
    var date: String? = null,
    var name: String? = null,
    var net: String? = null,
    var invoiceNumber: String? = null,
    var isRefund: Boolean = false
  )

  enum class State {
    UNKNOWN,
    WAITING_FOR_CONVERSION
  }

  fun run() {
    //registerPaypalTransactions()
    //registerAllMercariTransactions()

    getAllTransactions()
  }

  private fun registerPaypalTransactions() {
    val csvReader = CSVReaderBuilder(FileReader("/Users/grillett/Downloads/Download_1.CSV"))
      .withCSVParser(CSVParserBuilder().withSeparator(',').build())
      .build()

    // header
    val readNext = csvReader.readNext()
    val results = mutableListOf<PayPalData>()
    val existingInvoiceNumbers = mutableSetOf<String>()
    var previousData: PayPalData? = null
    var state: State = State.UNKNOWN
    var line: Array<String>? = csvReader.readNext()
    while (line != null) {
      // Do something with the data
      val date = line[0]
      val name = line[3]
      val type = line[4]
      val currency = line[6]
      val net = line[9]
      val invoiceNumber = line[15]
      line = csvReader.readNext()

      if (type == "General Withdrawal") continue
      if (type == "Payment Hold") continue
      if (type == "Payment Release") continue

      //println("$date \t $name \t $net \t $type \t $invoiceNumber")

      if (state == State.WAITING_FOR_CONVERSION && currency == "JPY" && (type == "User Initiated Currency Conversion" || type == "General Currency Conversion")) {
        previousData!!.net = net
        state = State.UNKNOWN
        results.add(previousData)
        previousData = null
      } else if (name != "" && state == State.UNKNOWN && currency == "JPY" && invoiceNumber == "" && (type == "General Payment" || type == "Mobile Payment")) {
        results.add(PayPalData(date, name, net, invoiceNumber))
      } else if (!existingInvoiceNumbers.contains(invoiceNumber) && name != "" && state == State.UNKNOWN && currency == "EUR" && (type == "Express Checkout Payment" || type == "General Payment" || type == "Mobile Payment")) {
        state = State.WAITING_FOR_CONVERSION
        previousData = PayPalData(name = name, date = date, invoiceNumber = invoiceNumber)
        if (invoiceNumber != "") {
          existingInvoiceNumbers.add(invoiceNumber)
        }
      } else if (name != "" && state == State.UNKNOWN && currency == "EUR" && (type == "Payment Refund")) {
        state = State.WAITING_FOR_CONVERSION
        previousData = PayPalData(name = name, date = date, isRefund = true)
      }
    }

    results
      .map {
        MercariData(
          "",
          "",
          "買い手: " + it.name!!, it.net!!.replace(",", ""),
          FREEE_FORMATTER.format(PAYPAL_TIME_FORMATTER.parse(it.date)),
          true,
          "",
          it.invoiceNumber ?: ""
        )
      }
      .forEach {
        println(it)
        createEntry(it, null, it.date)
      }

    println("Size: " + results.size)
  }

  private fun registerAllMercariTransactions() {
    scrapper.setUpAndWaitForConnection()
    val data = scrapper.scrap(0..2, "purchases/completed", true) // 13
    scrapper.close()

    data.forEach {
      val date = FREEE_FORMATTER.format(JAPANESE_TIME_FORMATTER.parse(it.date))

      if (date < START_DATE) {
        return
      }

      val receiptId = uploadReceipt(it, date)
      createEntry(it, receiptId, date)
    }

    println(data)
  }

  private fun uploadReceipt(data: MercariData, date: String): String {
    val requestBody: RequestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("company_id", FREEE_COMPANY_ID)
      .addFormDataPart("issue_date", date)
      .addFormDataPart("description", "メルカリ")
      .addFormDataPart(
        "receipt", "receipt.png",
        File(data.screenshotPath).asRequestBody("image/png".toMediaTypeOrNull())
      )
      .build()

    val execute = httpClient.newCall(
      Request.Builder()
        .url("https://api.freee.co.jp/api/1/receipts")
        .post(requestBody)
        .build()
    ).execute()

    val response = execute.body!!.string()
    val id = mapper.readTree(response).fields().next().value.get("id")

    execute.close()
    return id.asText()
  }

  private fun createEntry(data: MercariData, receiptId: String?, date: String) {
    val title = ObjectMapper().writeValueAsString(data.title)

    val json =
      if (receiptId != null) """
        {
          "company_id": $FREEE_COMPANY_ID,
          "issue_date": "$date",
          "type": "expense",
          "amount": ${data.price},
          "due_amount": 0,
          "ref_number": "${data.id}",
          "partner_id": 39191808,
          "status": "settled",
          "details": [
            {
              "amount": ${data.price}, 
              "tax_code": 20,
              "account_item_id": 523888560,
              "entry_side": "debit",
              "description": $title
            }
          ],
          "receipt_ids": [ $receiptId ]
        }
        """ else """
        {
          "company_id": $FREEE_COMPANY_ID,
          "issue_date": "$date",
          "type": "income",
          "amount": ${data.price},
          "due_amount": 0,
          "ref_number": "${data.id}",
          "partner_id": 41540878,
          "status": "settled",
          "details": [
            {
              "amount": ${data.price}, 
              "tax_code": 23,
              "account_item_id": 523888552,
              "entry_side": "credit",
              "description": $title
            }
          ]
        }
        """

    val body: RequestBody = RequestBody.create(JSON, json)
    val execute = httpClient.newCall(
      Request.Builder()
        .url("https://api.freee.co.jp/api/1/deals")
        .post(body)
        .build()
    ).execute()

    println(execute.body!!.string())

    execute.close()
  }

  private fun getAllTransactions() {
    val response = httpClient.newCall(
      Request.Builder()
        .url("https://api.freee.co.jp/api/1/deals?company_id=$FREEE_COMPANY_ID")
        .get()
        .build()
    ).execute()

    val fieldsIterator = mapper.readTree(response.body!!.string())
      .fields().next().value.asIterable()
    for (jsonNode in fieldsIterator) {
      println(
        "%s %s %s %s %s".format(
          if (jsonNode.get("receipts").size() == 1) "done" else "TODO",
          jsonNode.get("id"),
          jsonNode.get("type"),
          jsonNode.get("issue_date"),
          jsonNode.get("amount")
        )
      )
    }

    response.close()
  }

  private fun createHttpClient(): OkHttpClient {
    val clientBuilder = OkHttpClient.Builder()

    clientBuilder.authenticator(FreeeAuthenticator())
    clientBuilder.connectTimeout(10, TimeUnit.SECONDS)
    clientBuilder.writeTimeout(10, TimeUnit.SECONDS)
    clientBuilder.readTimeout(30, TimeUnit.SECONDS)

    return clientBuilder.build()
  }

  class FreeeAuthenticator: Authenticator {
    override fun authenticate(route: Route?, response: Response): Request {
      return response.request.newBuilder()
        .header("Authorization", "Bearer $FREEE_TOKEN")
        .build()
    }
  }
}