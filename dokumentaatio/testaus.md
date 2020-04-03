Testaus
=======

Hoidetaan tämä pois alta: algoritmin testaus osoittautui melko haastavaksi ja joiltain osin on ollut haastavaa määritellä mitä on mielekästä testata. Algoritmissa on paljon luonteeltaan yksinkertaisia, mutta satunnaisia osakomponentteja. Esimerkiksi *Marching Cubes* on pohjimmiltaan vain hakutaulun indeksointia nokkelalla hajautusfunktiolla ja sen jälkeen sieltä luetun datan muuntamista toiseen muotoon. Hakutaulujen koon vuoksi kaikkien vaihtoehtojen tyhjentävä testaus ei ole vaihtoehto, joten testaus on hieman hataralla pohjalla. Myös kohinafunktioiden testaus on painajainen, sillä niiden "oikeellisuus" on vaikea määrittää, sillä tulokset riippuvat paitsi valituista gradienteista, myös siemenluvuista, permutaatiotauluista sekä käytettävästä näytteenottotarkkuudesta.

Testaus itsessään, niiltä osin kun se on luonnistunut, tapahtuu *JUnit 5*:lla. Testien ajaminen onnistuu gradle taskilla `gradle test` ja kattavuusraportin saa ulos `gradle test jacocoTestReport`. Raportin selaimella avattava versio löytyy tämän jälkeen polusta `build/reports/jacoco/test/html/index.html`.

Yksikkötestausta suoritetaan yksittäisille osa-alueille. Esim. kohinafunktiosta testataan että tulokset jakautuvat koko halutulle arvojoukolle, *Marching Cubes*:sta tarkastellaan sen tuottaman mallin verteksien määrää ja eri tietotyypeistä testataan niiden oikeaa toimintaa. Kohinafunktion nopeutta testataan myös ajamalla sitä suuri määrä iteraatiota, vaatien että suoritus pysähtyy kyllin nopeasti. 

Yksikkötestausta tärkeämpänä osana on ollut "empiirisen testauksen" osuus, jota varten kirjoitin Vulkanilla ja LWJGL3:lla ohjelman generoidun 3d-mallin piirtämiseen. Tärkeys korostuu siinä että luolan visuaalisella ulkomuodolla on useissa kohdissa suurin rooli sen määrittelemisessä mikä toiminta on "oikein".
 
Yksikkötestien testikattavuus varsinkin marching cubesin ja tiheysfunktioiden osalta on osa-alue johon haluaisin panostaa, mutta niihin liittyy algoritmin luonteen vuoksi joitain ongelmia.
 