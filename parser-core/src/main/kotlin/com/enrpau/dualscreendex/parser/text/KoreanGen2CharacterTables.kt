package com.enrpau.dualscreendex.parser.text

/**
 * Exact generated projection of `Narishma-gb/pokegold-kr` Korean charmap tables at
 * `7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4`.
 */
internal object KoreanGen2CharacterTables {
    fun decode(lead: Int, trail: Int): String? {
        val table = TABLES[lead] ?: return null
        val glyph = table[trail]
        return glyph.takeUnless { it == UNMAPPED }?.toString()
    }

    private const val UNMAPPED = '\uFFFF'
    private val TABLES = mapOf(
        0x01 to (
            "\uFFFF가각간갇갈갉갊감갑값갓갔강갖갗" + // 00-0F
            "같갚갛개객갠갤갬갭갯갰갱갸갹갼걀" + // 10-1F
            "걋걍걔걘걜거걱건걷걸걺검겁것겄겅" + // 20-2F
            "겆겉겊겋게겐겔겜겝겟겠겡겨격겪견" + // 30-3F
            "겯결겹겸겻겼경곁계곈곌곕곗고곡곤" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "곧골곪곬곯곰곱곳공곶과곽관괄괆\uFFFF" + // 60-6F
            "\uFFFF괌괍괏광괘괜괠괩괬괭괴괵괸괼괻" + // 70-7F
            "굅굇굉교굔굘굡굣구국군굳굴굵굶굻" + // 80-8F
            "굼굽굿궁궂궈궉권궐궜궝궤궷귀귁귄" + // 90-9F
            "귈귐귑귓규균귤그극근귿글긁금급긋" + // A0-AF
            "긍긔기긱긴긷길긺김깁깃깅깆깊까깍" + // B0-BF
            "깎깐깔깖깜깝깟깠깡깥깨깩깬깰깸\uFFFF" + // C0-CF
            "\uFFFF깹깻깼깽꺄꺅꺌꺼꺽꺾껀껄껌껍껏" + // D0-DF
            "껐껑께껙껜껨껫껭껴껸껼꼇꼈꼍꼐꼬" + // E0-EF
            "꼭꼰꼲꼴꼼꼽꼿꽁꽂꽃꽈꽉꽐꽜꽝꽤" // F0-FF
        ),
        0x02 to (
            "꽥꽹꾀꾄꾈꾐꾑꾕꾜꾸꾹꾼꿀꿇꿈꿉" + // 00-0F
            "꿋꿍꿎꿔꿜꿨꿩꿰꿱꿴꿸뀀뀁뀄뀌뀐" + // 10-1F
            "뀔뀜뀝뀨끄끅끈끊끌끎끓끔끕끗끙\uFFFF" + // 20-2F
            "\uFFFF끝끼끽낀낄낌낍낏낑나낙낚난낟날" + // 30-3F
            "낡낢남납낫났낭낮낯낱낳내낵낸낼냄" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "냅냇냈냉냐냑냔냘냠냥너넉넋넌널넒" + // 60-6F
            "넓넘넙넛넜넝넣네넥넨넬넴넵넷넸넹" + // 70-7F
            "녀녁년녈념녑녔녕녘녜녠노녹논놀놂" + // 80-8F
            "놈놉놋농높놓놔놘놜놨뇌뇐뇔뇜뇝\uFFFF" + // 90-9F
            "\uFFFF뇟뇨뇩뇬뇰뇹뇻뇽누눅눈눋눌눔눕" + // A0-AF
            "눗눙눠눴눼뉘뉜뉠뉨뉩뉴뉵뉼늄늅늉" + // B0-BF
            "느늑는늘늙늚늠늡늣능늦늪늬늰늴니" + // C0-CF
            "닉닌닐닒님닙닛닝닢다닥닦단닫달닭" + // D0-DF
            "닮닯닳담답닷닸당닺닻닿대댁댄댈댐" + // E0-EF
            "댑댓댔댕\uFFFF더덕덖던덛덜덞덟덤덥\uFFFF" // F0-FF
        ),
        0x03 to (
            "\uFFFF덧덩덫덮데덱덴델뎀뎁뎃뎄뎅뎌뎐" + // 00-0F
            "뎔뎠뎡뎨뎬도독돈돋돌돎\uFFFF돔돕돗동" + // 10-1F
            "돛돝돠돤돨돼됐되된될됨됩됫됴두둑" + // 20-2F
            "둔둘둠둡둣둥둬뒀뒈뒝뒤뒨뒬뒵뒷뒹" + // 30-3F
            "듀듄듈듐듕드득든듣들듦듬듭듯등듸" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "디딕딘딛딜딤딥딧딨딩딪따딱딴딸\uFFFF" + // 60-6F
            "\uFFFF땀땁땃땄땅땋때땍땐땔땜땝땟땠땡" + // 70-7F
            "떠떡떤떨떪떫떰떱떳떴떵떻떼떽뗀뗄" + // 80-8F
            "뗌뗍뗏뗐뗑뗘뗬또똑똔똘똥똬똴뙈뙤" + // 90-9F
            "뙨뚜뚝뚠뚤뚫뚬뚱뛔뛰뛴뛸뜀뜁뜅뜨" + // A0-AF
            "뜩뜬뜯뜰뜸뜹뜻띄띈띌띔띕띠띤띨띰" + // B0-BF
            "띱띳띵라락란랄람랍랏랐랑랒랖랗\uFFFF" + // C0-CF
            "뢔래랙랜랠램랩랫랬랭랴략랸럇량러" + // D0-DF
            "럭런럴럼럽럿렀렁렇레렉렌렐렘렙렛" + // E0-EF
            "렝려력련렬렴렵렷렸령례롄롑롓로록" // F0-FF
        ),
        0x04 to (
            "론롤롬롭롯롱롸롼뢍뢨뢰뢴뢸룀룁룃" + // 00-0F
            "룅료룐룔룝룟룡루룩룬룰룸룹룻룽뤄" + // 10-1F
            "뤘뤠뤼뤽륀륄륌륏륑류륙륜률륨륩\uFFFF" + // 20-2F
            "\uFFFF륫륭르륵른를름릅릇릉릊릍릎리릭" + // 30-3F
            "린릴림립릿링마막만많맏말맑맒맘맙" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "맛망맞맡맣매맥맨맬맴맵맷맸맹맺먀" + // 60-6F
            "먁먈먕머먹먼멀멂멈멉멋멍멎멓메멕" + // 70-7F
            "멘멜멤멥멧멨멩며멱면멸몃몄명몇몌" + // 80-8F
            "모목몫몬몰몲몸몹못몽뫄뫈뫘뫙뫼\uFFFF" + // 90-9F
            "\uFFFF묀묄묍묏묑묘묜묠묩묫무묵묶문묻" + // A0-AF
            "물묽묾뭄뭅뭇뭉뭍뭏뭐뭔뭘뭡뭣뭬뮈" + // B0-BF
            "뮌뮐뮤뮨뮬뮴뮷므믄믈믐믓미믹민믿" + // C0-CF
            "밀밂밈밉밋밌밍및밑바박밖밗반받발" + // D0-DF
            "밝밞밟밤밥밧방밭배백밴밸뱀뱁뱃뱄" + // E0-EF
            "뱅뱉뱌뱍뱐뱝버벅번벋벌벎범법벗\uFFFF" // F0-FF
        ),
        0x05 to (
            "\uFFFF벙벚베벡벤벧벨벰벱벳벴벵벼벽변" + // 00-0F
            "별볍볏볐병볕볘볜보복볶본볼봄봅봇" + // 10-1F
            "봉봐봔봤봬뵀뵈뵉뵌뵐뵘뵙뵤뵨부북" + // 20-2F
            "분붇불붉붊붐붑붓붕붙붚붜붤붰붸뷔" + // 30-3F
            "뷕뷘뷜뷩뷰뷴뷸븀븃븅브븍븐블븜븝" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "븟비빅빈빌빎빔빕빗빙빚빛빠빡빤\uFFFF" + // 60-6F
            "\uFFFF빨빪빰빱빳빴빵빻빼빽뺀뺄뺌뺍뺏" + // 70-7F
            "뺐뺑뺘뺙뺨뻐뻑뻔뻗뻘뻠뻣뻤뻥뻬뼁" + // 80-8F
            "뼈뼉뼘뼙뼛뼜뼝뽀뽁뽄뽈뽐뽑뽕뾔뾰" + // 90-9F
            "뿅뿌뿍뿐뿔뿜뿟뿡쀼쁑쁘쁜쁠쁨쁩삐" + // A0-AF
            "삑삔삘삠삡삣삥사삭삯산삳살삵삶삼" + // B0-BF
            "삽삿샀상샅새색샌샐샘샙샛샜생샤\uFFFF" + // C0-CF
            "\uFFFF샥샨샬샴샵샷샹섀섄섈섐섕서석섞" + // D0-DF
            "섟선섣설섦섧섬섭섯섰성섶세섹센셀" + // E0-EF
            "셈셉셋셌셍셔셕션셜셤셥셧셨셩셰셴" // F0-FF
        ),
        0x06 to (
            "셸솅소속솎손솔솖솜솝솟송솥솨솩솬" + // 00-0F
            "솰솽쇄쇈쇌쇔쇗쇘쇠쇤쇨쇰쇱쇳쇼쇽" + // 10-1F
            "숀숄숌숍숏숑수숙순숟술숨숩숫숭쌰" + // 20-2F
            "쎼숯숱숲숴쉈쉐쉑쉔쉘쉠쉥쉬쉭쉰쉴" + // 30-3F
            "쉼쉽쉿슁슈슉슐슘슛슝스슥슨슬슭슴" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "습슷승시식신싣실싫심십싯싱싶싸싹" + // 60-6F
            "싻싼쌀쌈쌉쌌쌍쌓쌔쌕쌘쌜쌤쌥쌨쌩" + // 70-7F
            "썅써썩썬썰썲썸썹썼썽쎄쎈쎌쏀쏘쏙" + // 80-8F
            "쏜쏟쏠쏢쏨쏩쏭쏴쏵쏸쐈쐐쐤쐬쐰\uFFFF" + // 90-9F
            "쓔쐴쐼쐽쑈쑤쑥쑨쑬쑴쑵쑹쒀쒔쒜쒸" + // A0-AF
            "쒼쓩쓰쓱쓴쓸쓺쓿씀씁씌씐씔씜씨씩" + // B0-BF
            "씬씰씸씹씻씽아악안앉않알앍앎앓암" + // C0-CF
            "압앗았앙앝앞애액앤앨앰앱앳앴앵야" + // D0-DF
            "약얀얄얇얌얍얏양얕얗얘얜얠얩어억" + // E0-EF
            "언얹얻얼얽얾엄업없엇었엉엊엌엎\uFFFF" // F0-FF
        ),
        0x07 to (
            "\uFFFF에엑엔엘엠엡엣엥여역엮연열엶엷" + // 00-0F
            "염엽엾엿였영옅옆옇예옌옐옘옙옛옜" + // 10-1F
            "오옥온올옭옮옰옳옴옵옷옹옻와왁완" + // 20-2F
            "왈왐왑왓왔왕왜왝왠왬왯왱외왹왼욀" + // 30-3F
            "욈욉욋욍요욕욘욜욤욥욧용우욱운울" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "욹욺움웁웃웅워웍원월웜웝웠웡웨\uFFFF" + // 60-6F
            "\uFFFF웩웬웰웸웹웽위윅윈윌윔윕윗윙유" + // 70-7F
            "육윤율윰윱윳융윷으윽은을읆음읍읏" + // 80-8F
            "응읒읓읔읕읖읗의읜읠읨읫이익인일" + // 90-9F
            "읽읾잃임입잇있잉잊잎자작잔잖잗잘" + // A0-AF
            "잚잠잡잣잤장잦재잭잰잴잼잽잿쟀쟁" + // B0-BF
            "쟈쟉쟌쟎쟐쟘쟝쟤쟨쟬저적전절젊\uFFFF" + // C0-CF
            "\uFFFF점접젓정젖제젝젠젤젬젭젯젱져젼" + // D0-DF
            "졀졈졉졌졍졔조족존졸졺좀좁좃종좆" + // E0-EF
            "좇좋좌좍좔좝좟좡좨좼좽죄죈죌죔죕" // F0-FF
        ),
        0x08 to (
            "죗죙죠죡죤죵주죽준줄줅줆줌줍줏중" + // 00-0F
            "줘줬줴쥐쥑쥔쥘쥠쥡쥣쥬쥰쥴쥼즈즉" + // 10-1F
            "즌즐즘즙즛증지직진짇질짊짐집짓\uFFFF" + // 20-2F
            "쬬징짖짙짚짜짝짠짢짤짧짬짭짯짰짱" + // 30-3F
            "째짹짼쨀쨈쨉쨋쨌쨍쨔쨘쨩쩌쩍쩐쩔" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "쩜쩝쩟쩠쩡쩨쩽쪄쪘쪼쪽쫀쫄쫌쫍쫏" + // 60-6F
            "쫑쫓쫘쫙쫠쫬쫴쬈쬐쬔쬘쬠쬡쭁쭈쭉" + // 70-7F
            "쭌쭐쭘쭙쭝쭤쭸쭹쮜쮸쯔쯤쯧쯩찌찍" + // 80-8F
            "찐찔찜찝찡찢찧차착찬찮찰참찹찻\uFFFF" + // 90-9F
            "\uFFFF찼창찾채책챈챌챔챕챗챘챙챠챤챦" + // A0-AF
            "챨챰챵처척천철첨첩첫첬청체첵첸첼" + // B0-BF
            "쳄쳅쳇쳉쳐쳔쳤쳬쳰촁초촉촌촐촘촙" + // C0-CF
            "촛총촤촨촬촹최쵠쵤쵬쵭쵯쵱쵸춈추" + // D0-DF
            "축춘출춤춥춧충춰췄췌췐취췬췰췸췹" + // E0-EF
            "췻췽츄츈츌츔츙츠측츤츨츰츱츳층\uFFFF" // F0-FF
        ),
        0x09 to (
            "\uFFFF치칙친칟칠칡침칩칫칭카칵칸칼캄" + // 00-0F
            "캅캇캉캐캑캔캘캠캡캣캤캥캬캭컁커" + // 10-1F
            "컥컨컫컬컴컵컷컸컹케켁켄켈켐켑켓" + // 20-2F
            "켕켜켠켤켬켭켯켰켱켸코콕콘콜콤콥" + // 30-3F
            "콧콩콰콱콴콸쾀쾅쾌쾡쾨쾰쿄쿠쿡쿤" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "쿨쿰쿱쿳쿵쿼퀀퀄퀑퀘퀭퀴퀵퀸퀼\uFFFF" + // 60-6F
            "\uFFFF큄큅큇큉큐큔큘큠크큭큰클큼큽킁" + // 70-7F
            "키킥킨킬킴킵킷킹타탁탄탈탉탐탑탓" + // 80-8F
            "탔탕태택탠탤탬탭탯탰탱탸턍터턱턴" + // 90-9F
            "털턺텀텁텃텄텅테텍텐텔템텝텟텡텨" + // A0-AF
            "텬텼톄톈토톡톤톨톰톱톳통톺톼퇀퇘" + // B0-BF
            "퇴퇸툇툉툐투툭툰툴툼툽툿퉁퉈퉜\uFFFF" + // C0-CF
            "\uFFFF퉤튀튁튄튈튐튑튕튜튠튤튬튱트특" + // D0-DF
            "튼튿틀틂틈틉틋틔틘틜틤틥티틱틴틸" + // E0-EF
            "팀팁팃팅파팍팎판팔팖팜팝팟팠팡팥" // F0-FF
        ),
        0x0A to (
            "패팩팬팰팸팹팻팼팽퍄퍅퍼퍽펀펄펌" + // 00-0F
            "펍펏펐펑페펙펜펠펨펩펫펭펴편펼폄" + // 10-1F
            "폅폈평폐폘폡폣포폭폰폴폼폽폿퐁\uFFFF" + // 20-2F
            "\uFFFF퐈퐝푀푄표푠푤푭푯푸푹푼푿풀풂" + // 30-3F
            "품풉풋풍풔풩퓌퓐퓔퓜퓟퓨퓬퓰퓸퓻" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "퓽프픈플픔픕픗피픽핀필핌핍핏핑하" + // 60-6F
            "학한할핥함합핫항해핵핸핼햄햅햇했" + // 70-7F
            "행햐향허헉헌헐헒험헙헛헝헤헥헨헬" + // 80-8F
            "헴헵헷헹혀혁현혈혐협혓혔형혜혠\uFFFF" + // 90-9F
            "\uFFFF혤혭호혹혼홀홅홈홉홋홍홑화확환" + // A0-AF
            "활홧황홰홱홴횃횅회획횐횔횝횟횡효" + // B0-BF
            "횬횰횹횻후훅훈훌훑훔훗훙훠훤훨훰" + // C0-CF
            "훵훼훽휀휄휑휘휙휜휠휨휩휫휭휴휵" + // D0-DF
            "휸휼흄흇흉흐흑흔흖흗흘흙흠흡흣흥" + // E0-EF
            "흩희흰흴흼흽힁히힉힌힐힘힙힛힝\uFFFF" // F0-FF
        ),
        0x0B to (
            "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎㄲㄸ" + // 00-0F
            "ㅃㅆㅉ\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 10-1F
            "ㅏㅑㅓㅕㅗㅛㅜㅠㅡㅣㅐㅒㅔㅖㅘㅙ" + // 20-2F
            "ㅚㅝㅞㅟㅢ\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF_—" + // 30-3F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 40-4F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 50-5F
            "「」『』()!?-~…,.\uFFFF\uFFFF\uFFFF" + // 60-6F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 70-7F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 80-8F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // 90-9F
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // A0-AF
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // B0-BF
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // C0-CF
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // D0-DF
            "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF" + // E0-EF
            "0123456789\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF " // F0-FF
        ),
    )
}
