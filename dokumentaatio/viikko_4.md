Viikko 4.
---------
*Työmäärä tällä viikolla ~8 tuntia*

Hieman hitaampi viikko muiden kiireiden puitteissa tällä viikolla. Suurimmat muutokset olivat paikoitellen tapahtuneissa standardikirjaston `Stream`-operaatioista pois siirtymisessä ja `ArrayListin` korvaamisessa omalla toteutuksella. Testikattavuutta sain jonkin verran parannettua, mutta algoritmin luonne tekee testaamisesta haastavaa.

Tiheysfunktioiden kanssa viilasin myös jälleen jonkin verran ja viimein niiden vakaus tuntuu olevan sellaisella asteella että jätän ne todennäköisesti toistaiseksi sikseen.

Seuraavaksi pitäisi vähitellen saada pilkottua näytteenottoavaruus järkevämpään muotoon jotta suurempien luolien generointi onnistuisi. Idea tällä hetkellä olisi generoida aluksi pelkkä luolan polku ja pilkkoa näytteenottoavaruus sitten `w * h * d` kokoisiin lohkoihin joista kunkin näytteenottoavaruus ja lopulta renderöitävä malli voidaan generoida erikseen. Tämän pitäisi mahdollistaa huomattavasti suurempien luolien generointi. Yksittäisen lohkon suorituskykyä on tarkoitus yrittää parantaa k-uloitteisella puulla tai octreella.
