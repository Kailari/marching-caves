Käyttöohje
==========

Ohjelmalla ei ole kunnollista käyttöliittymää, eikä suoritettavan `.jar`-tiedoston jakaminen siksi ollut kovin mielekästä. Ohjelman käyttäminen tapahtuu suorittamalla sitä gradlella.

Ohjelman suorittaminen
----------------------
Ohjelman suorittaminen onnistuu ajamalla projektin juuressa
```
.\gradlew run
```
tai mikäli yhteensopiva `gradle`-versio on asennettuna
```
gradle run
```

Testit suoritetaan vastaavasti gradle taskilla
```
.\gradlew test
```

Generointiparametrien muuttaminen
---------------------------------
Parametrien muuttaminen tapahtuu muokkaamalla `Main.java` tiedostosta `Application` konstruktorille annettavia arvoja.

| Parametrin nimi  | kuvaus                                                            |
|------------------|-------------------------------------------------------------------|
| `caveLength`     | Luolan pituus. *Satunnaiskulun* askelten lukumäärä.               |
| `nodeSpacing`    | Yhden askeleen pituus.                                            |
| `surfaceLevel`   | Tiheys jota pienemmät arvot ovat ilmaa. Luku väliltä 0..1         |
| `samplesPerUnit` | Montako näytteenottopistettä yhtä tilayksikköä kohti tulisi ottaa. Arvot `0.25`, `0.5` ja `1.0` toimivat melko hyvin luolan pituudesta riippuen. *"Korkeat resoluutiot"* `4.0` tai `8.0` toimivat vain erittäin pienillä luolilla. |
| `floorFlatness` | Luku väliltä 0..1, kuvaa painokerrointa paljonko luolan "lattian" tasoitukselle annetaan painoarvoa lopullista tiheysarvoa laskettaessa. |
| `caveRadius` | Luolan halkaisija. Tämä on polun ympärille generoitavan "perustiheyden", eli etäisyydestä polkuun aiheutuvan tiheyden muutoksen halkaisija. Aineen tiheys skaalautuu lineaarisesti etäisyyden polkuun suhteen siten, että tämän etäisyyden jälkeen tiheys on `1.0`. Esim. siis etäisyydellä `caveRadius / 2.0` tiheys on `0.5`, etäisyydellä `0` myös tiheys on `0`, jne. |
| `maxInfluenceRadius` | Etäisyys polusta, jonka jälkeen aineen tiheys on aina `1.0`. Noise yms. skaalataan lopulliseen tiheysarvoon siten, että tiheys on `1.0` kaikilla etäisyyksillä jotka ovat tätä arvoa suuremmat. |
| `seed` | Generaattorin siemenluku. Generointi on deterministinen siemenlukuun nähden, eli samalla siemenluvulla luodaaan aina samanlainen luola. |