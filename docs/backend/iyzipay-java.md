# Iyzipay Java SDK (2.0.140) quick reference

This project integrates with iyzico for payments. To develop or troubleshoot the SDK locally, use the official iyzipay-java package from Maven Central.

## Installation

Add the library dependency (already included in `build.gradle`):

```groovy
dependencies {
    implementation 'com.iyzipay:iyzipay-java:2.0.140'
}
```

If you manage dependencies manually, download the JARs from the links below:

- iyzipay-java 2.0.140: <https://github.com/iyzico/iyzipay-java/releases/latest>
- Gson 2.5: <http://mvnrepository.com/artifact/com.google.code.gson/gson/2.5>
- Apache Commons Lang 3.4: <http://mvnrepository.com/artifact/org.apache.commons/commons-lang3/3.4>
- (For running samples) JUnit 4.12: <http://mvnrepository.com/artifact/junit/junit/4.12>

## Basic usage example

```java
Options options = new Options();
options.setApiKey("your api key");
options.setSecretKey("your secret key");
options.setBaseUrl("https://sandbox-api.iyzipay.com");

CreatePaymentRequest request = new CreatePaymentRequest();
request.setLocale(Locale.TR.getValue());
request.setConversationId("123456789");
request.setPrice(new BigDecimal("1"));
request.setPaidPrice(new BigDecimal("1.2"));
request.setCurrency(Currency.TRY.name());
request.setInstallment(1);
request.setBasketId("B67832");
request.setPaymentChannel(PaymentChannel.WEB.name());
request.setPaymentGroup(PaymentGroup.PRODUCT.name());

PaymentCard paymentCard = new PaymentCard();
paymentCard.setCardHolderName("John Doe");
paymentCard.setCardNumber("5528790000000008");
paymentCard.setExpireMonth("12");
paymentCard.setExpireYear("2030");
paymentCard.setCvc("123");
paymentCard.setRegisterCard(0);
request.setPaymentCard(paymentCard);

Buyer buyer = new Buyer();
buyer.setId("BY789");
buyer.setName("John");
buyer.setSurname("Doe");
buyer.setGsmNumber("+905350000000");
buyer.setEmail("email@email.com");
buyer.setIdentityNumber("74300864791");
buyer.setLastLoginDate("2015-10-05 12:43:35");
buyer.setRegistrationDate("2013-04-21 15:12:09");
buyer.setRegistrationAddress("Nidakule Göztepe, Merdivenköy Mah. Bora Sok. No:1");
buyer.setIp("85.34.78.112");
buyer.setCity("Istanbul");
buyer.setCountry("Turkey");
buyer.setZipCode("34732");
request.setBuyer(buyer);

Address shippingAddress = new Address();
shippingAddress.setContactName("Jane Doe");
shippingAddress.setCity("Istanbul");
shippingAddress.setCountry("Turkey");
shippingAddress.setAddress("Nidakule Göztepe, Merdivenköy Mah. Bora Sok. No:1");
shippingAddress.setZipCode("34742");
request.setShippingAddress(shippingAddress);

Address billingAddress = new Address();
billingAddress.setContactName("Jane Doe");
billingAddress.setCity("Istanbul");
billingAddress.setCountry("Turkey");
billingAddress.setAddress("Nidakule Göztepe, Merdivenköy Mah. Bora Sok. No:1");
billingAddress.setZipCode("34742");
request.setBillingAddress(billingAddress);

List<BasketItem> basketItems = new ArrayList<>();
BasketItem firstBasketItem = new BasketItem();
firstBasketItem.setId("BI101");
firstBasketItem.setName("Binocular");
firstBasketItem.setCategory1("Collectibles");
firstBasketItem.setCategory2("Accessories");
firstBasketItem.setItemType(BasketItemType.PHYSICAL.name());
firstBasketItem.setPrice(new BigDecimal("0.3"));
basketItems.add(firstBasketItem);

BasketItem secondBasketItem = new BasketItem();
secondBasketItem.setId("BI102");
secondBasketItem.setName("Game code");
secondBasketItem.setCategory1("Game");
secondBasketItem.setCategory2("Online Game Items");
secondBasketItem.setItemType(BasketItemType.VIRTUAL.name());
secondBasketItem.setPrice(new BigDecimal("0.5"));
basketItems.add(secondBasketItem);

BasketItem thirdBasketItem = new BasketItem();
thirdBasketItem.setId("BI103");
thirdBasketItem.setName("Usb");
thirdBasketItem.setCategory1("Electronics");
thirdBasketItem.setCategory2("Usb / Cable");
thirdBasketItem.setItemType(BasketItemType.PHYSICAL.name());
thirdBasketItem.setPrice(new BigDecimal("0.2"));
basketItems.add(thirdBasketItem);
request.setBasketItems(basketItems);

Payment payment = Payment.create(request, options);
```

The sandbox base URL and test cards are documented at <https://sandbox-api.iyzipay.com>. You can run sample flows with Maven:

```bash
mvn test -Dtest=PaymentSample -DbaseUrl=https://sandbox-api.iyzipay.com -DapiKey=yourApiKey -DsecretKey=yourSecretKey
```

See additional card numbers and APM samples in the official repository if you need to simulate specific outcomes during testing.
