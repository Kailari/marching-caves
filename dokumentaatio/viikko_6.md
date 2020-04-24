Viikko 6.
---------
*Työmäärä tällä viikolla ~20 tuntia*

Täysiä päätyyn ja päädystä läpi. Laadin viikon aluksi itselleni viisivaiheisen toteutussuunitelman jolla algoritmia saadaan ajettua useissa säikeissä samanaikaisesti ja sitä noudattaen sain melko hyviä tuloksia aikaan.

Luolan 3d mallin pilkkominen osiin oli suoraviivaista näytteenottoavaruuden lohkoja hyödyntäen, mutta visualisoinnin muistiongelmat piti saada ratkaistuksi; jota varten kirjoittelin hyvin yksinkertaisen muistimanageripalikan Vulkanin muistiallokaatioiden hallintaan. Tämä korjasi jo itsessään suuren osan suurempien luolien generointiin liittyvistä ongelmista, ja nyt `caveLength=16000, SBS=2.0` generoitui ilman ongelmia.

Tästä edelleen asioiden siirtäminen useaan säikeeseen onnistui todella helposti, koska flood-fillin myötä Marching Cubesin suoritus käytti jo valmiiksi eräänlaista "työjonoa". Tämä jono piti vain muuttaa sellaisen muotoon että sieltä saadaan vedettyä useilla säikeillä työtä ulos järkevästi.

Tämä viimeisin tuotti kuitenkin päänvaivaa, enkä saanut omaa `ThreadPool` toteutusta toimimaan kovin hyvällä suorituskyvyllä. Päädyin heittämään oman toteutukseni roskakoriin ja käytin hävyttömästi vain suoraan standardikirjaston `ExecutorService` ja valmiita thread pooleja. Standardikirjaston toteutus oli joitain kymmeniä kertoja nopeampi ja huomattavasti vakaampi kuin omat räpellykseni, joten en halunnut tuhlata tähän enää yhtään enempää aikaa.

Suuri osa viikon työtunneista kului säikeiden synkronoinnin optimointiin ja muuhun suorituskyvyn ja muistinvarauksien optimointiin.
