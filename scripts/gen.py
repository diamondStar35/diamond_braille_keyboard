import json
id_map = """af-g2|af|6|2|ubc-af
akk-g1|akk|6|1|U.S.
akk-g1-3|akk|6|1|Borger
akk-g1-4|akk|6|1|U.S.
akk-g1-akkborger|akk|6|1|akk-borger
ar-comp8|ar|8|-|
ar-g1|ar|6|1|
ar-g2|ar|6|2|
as-g1|as|6|1|
as-IN-g1|as_IN|6|1|
aw-IN-g1|aw_IN|6|1|
awa-g1|awa|6|1|
ba-g1|ba|6|1|
be-comp8|be|8|-|
be-g1|be|6|1|detailed
be-g1-2|be|6|1|
be-IN-g1|be_IN|6|1|
bg-comp8|bg|8|-|
bg-g1|bg|6|1|
bg-g1-2|bg|6|1|
bh-g1|bh|6|1|
bh-g1-2|bh|6|1|
bn-g1|bn|6|1|
bo-comp8|bo|8|-|
bo-g1|bo|6|1|
br-IN-g1|br_IN|6|1|
bra-g1|bra|6|1|
ca-g1|ca|6|1|
ca-g1-2|ca|6|1|
chr-g1|chr|6|1|
ckb-g1|ckb|6|1|
ckb-g1-2|ckb|6|1|
cmn-CN-g1|cmn_CN|6|1|cmn-traditional
cmn-CN-g1-cmntwocell|cmn_CN|6|1|cmn-two-cell
cmn-TW-g1|cmn_TW|6|1|cmn-bopomofo
cop-comp8|cop|8|-|
cop-g1|cop|6|1|
cs-comp8|cs|8|-|
cs-g1|cs|6|1|
cy-g1|cy|6|1|
cy-g2|cy|6|2|
cy-g2-2|cy|6|2|
da-comp8|da|8|-|2022
da-comp8-3|da|8|-|2022
da-comp8-4|da|8|-|1993
da-comp8-5|da|8|-|2022
da-comp8-6|da|8|-|1993
da-comp8-7|da|8|-|1993
da-comp8-ddp|da|8|-|1993
da-g1|da|6|1|1993
da-g1-3|da|6|1|1993
da-g1-4|da|6|1|1993
da-g1-5|da|6|1|1993
da-g1-ddp|da|6|1|2022
da-g2|da|6|2|1993
da-g2-3|da|6|2|1993
da-g2-ddp|da|6|2|2022
de-comp6|de|6|-|
de-comp8|de|8|-|
de-g0|de|6|0|
de-g0-2|de|6|0|detailed
de-g1|de|6|1|
de-g1-2|de|6|1|detailed
de-g2|de|6|2|
de-g2-2|de|6|2|detailed
dra-comp8|dra|8|-|
dra-g1|dra|6|1|
el-g1|el|6|1|
en-CA-comp8|en_CA|8|-|Canada
en-g1|en|6|1|ueb
en-g2|en|6|2|ueb
en-g3|en|6|3|
en-GB-comp8|en_GB|8|-|bauk
en-GB-g1|en_GB|6|1|bauk
en-GB-g2|en_GB|6|2|
en-GB-g2-bauk|en_GB|6|2|bauk
en-IN-g1|en_IN|6|1|
en-US-comp6|en_US|6|-|ebae
en-US-comp8|en_US|8|-|
en-US-comp8-2|en_US|8|-|
en-US-g1|en_US|6|1|ebae
en-US-g2|en_US|6|2|
en-US-g2-ebae|en_US|6|2|ebae
eo-g1|eo|6|1|
eo-g1-2|eo|6|1|
es-comp8|es|8|-|
es-g1|es|6|1|
es-g1-2|es|6|1|
es-g2|es|6|2|
es-NO-g1|es_NO|6|1|Norway
et-comp8|et|8|-|
et-g1|et|6|1|
fa-comp8|fa|8|-|
fa-g1|fa|6|1|
fi-comp8|fi|8|-|
fi-g1|fi|6|1|
fil-g2|fil|6|2|fbc
fr-comp8|fr|8|-|
fr-g1|fr|6|1|
fr-g2|fr|6|2|
ga-g1|ga|6|1|
ga-g2|ga|6|2|
gd-comp8|gd|8|-|
gd-g1|gd|6|1|
gez-g1|gez|6|1|
gon-g1|gon|6|1|
gon-g1-2|gon|6|1|
grc-EN-g1|grc_EN|6|1|composed
grc-EN-g1-grcinternationalen|grc_EN|6|1|decomposed
grc-ES-g1|grc_ES|6|1|grc-international-es
gu-g1|gu|6|1|
gu-IN-g1|gu_IN|6|1|
haw-g1|haw|6|1|
hbo-g1|hbo|6|1|IHBC
hbo-g1-ihbcmcallister|hbo|6|1|IHBC-McAllister
hbo-g1-katz|hbo|6|1|Katz
he-comp8|he|8|-|modern
he-IL-g1|he_IL|6|1|modern
hi-g1|hi|6|1|
hi-IN-g1|hi_IN|6|1|
hr-comp8|hr|8|-|
hr-comp8-2|hr|8|-|
hr-g1|hr|6|1|
hr-g1-2|hr|6|1|
hu-comp8|hu|8|-|
hu-g1|hu|6|1|
hu-g2|hu|6|2|
hy-comp8|hy|8|-|
hy-g1|hy|6|1|
is-g1|is|6|1|
is-g1-2|is|6|1|
it-comp8|it|8|-|
it-g1|it|6|1|monza
iu-g1|iu|6|1|
ja-comp8|ja|8|-|Kantenji
ja-comp8-2|ja|8|-|Kantenji
ja-g1|ja|6|1|Rokuten Kanji
ka-g1|ka|6|1|
ka-IN-g1|ka_IN|6|1|
kha-g1|kha|6|1|
kk-g1|kk|6|1|
km-g1|km|6|1|cambodia
kmr-g0|kmr|6|0|
kn-g1|kn|6|1|
ko-g1|ko|6|1|
ko-g1-2|ko|6|1|2006
ko-g2|ko|6|2|
ko-g2-2|ko|6|2|2006
kok-g1|kok|6|1|
kok-g1-2|kok|6|1|
kru-g1|kru|6|1|
kru-g1-2|kru|6|1|
ks-IN-g1|ks_IN|6|1|
lg-g1|lg|6|1|
lo-g1|lo|6|1|Laos
lt-comp8|lt|8|-|
lt-g1|lt|6|1|
lt-g1-2|lt|6|1|
lv-g1|lv|6|1|
lv-g1-2|lv|6|1|
mi-g1|mi|6|1|
mk-g1|mk|6|1|
ml-g1|ml|6|1|
ml-IN-g1|ml_IN|6|1|
mn-comp8|mn|8|-|
mn-comp8-2|mn|8|-|
mn-IN-g1|mn_IN|6|1|
mni-g1|mni|6|1|
mr-g1|mr|6|1|
mr-IN-g1|mr_IN|6|1|
ms-g2|ms|6|2|
mt-comp8|mt|8|-|
mt-g1|mt|6|1|
mun-g1|mun|6|1|
mun-g1-2|mun|6|1|
mwr-g1|mwr|6|1|
mwr-g1-2|mwr|6|1|
my-g1|my|6|1|
my-g2|my|6|2|
ne-g1|ne|6|1|
ne-g1-2|ne|6|1|
nl-comp8|nl|8|-|
nl-g0|nl|6|0|
no-comp8|no|8|-|fallback-6dot
no-comp8-2|no|8|-|
no-comp8-3|no|8|-|
no-g0|no|6|0|
no-g1|no|6|1|
no-g1-2|no|6|1|
no-g2|no|6|2|
no-g3|no|6|3|
np-IN-g1|np_IN|6|1|
ny-g1|ny|6|1|
or-g1|or|6|1|
or-IN-g1|or_IN|6|1|
ovd-comp8|ovd|8|-|
ovd-g0|ovd|6|0|
pa-g1|pa|6|1|
pi-g1|pi|6|1|
pi-g1-2|pi|6|1|
pl-comp8|pl|8|-|
pl-g1|pl|6|1|
pl-g1-2|pl|6|1|
pt-comp6|pt|6|-|
pt-comp8|pt|8|-|
pt-g1|pt|6|1|
pt-g2|pt|6|2|
pt-g2-2|pt|6|2|
pu-IN-g1|pu_IN|6|1|
ro-comp8|ro|8|-|
ro-g0|ro|6|0|
ro-g1|ro|6|1|
ru-comp6|ru|6|-|
ru-comp8|ru|8|-|
ru-g1|ru|6|1|
rw-g1|rw|6|1|
sa-g1|sa|6|1|
sa-IN-g1|sa_IN|6|1|
sah-g1|sah|6|1|
sd-g1|sd|6|1|
se-g1|se|6|1|
si-IN-g1|si_IN|6|1|
sin-g1|sin|6|1|
sk-g1|sk|6|1|
sk-g1-2|sk|6|1|
sk-g1-3|sk|6|1|
sl-comp8|sl|8|-|
sl-SI-g1|sl_SI|6|1|
smi-comp8|smi|8|-|
smi-g0|smi|6|0|
sr-g1|sr|6|1|
st-g1|st|6|1|
st-g2|st|6|2|
sv-comp8|sv|8|-|1989
sv-comp8-2|sv|8|-|1996
sv-comp8-3|sv|8|-|detailed
sv-comp8-4|sv|8|-|normal
sv-comp8-5|sv|8|-|detailed
sv-comp8-6|sv|8|-|normal
sv-comp8-7|sv|8|-|detailed
sv-comp8-8|sv|8|-|normal
sv-g0|sv|6|0|normal
sv-g0-2|sv|6|0|detailed
sv-g0-3|sv|6|0|phonetics
sv-g1|sv|6|1|normal
sv-g1-2|sv|6|1|detailed
sv-g2|sv|6|2|normal
sv-g2-2|sv|6|2|detailed
sw-KE-g1|sw_KE|6|1|
sw-KE-g1-2|sw_KE|6|1|.
sw-KE-g1-3|sw_KE|6|1|.
sw-KE-g1-4|sw_KE|6|1|.4
sw-KE-g1-5|sw_KE|6|1|.5
sw-KE-g2|sw_KE|6|2|
syc-g1|syc|6|1|
ta-comp8|ta|8|-|
ta-g1|ta|6|1|
ta-g1-2|ta|6|1|
te-g1|te|6|1|
te-IN-g1|te_IN|6|1|
th-comp8|th|8|-|
th-g0|th|6|0|
th-g1|th|6|1|
th-g2|th|6|2|
tr-comp8|tr|8|-|
tr-comp8-2|tr|8|-|
tr-g1|tr|6|1|
tr-g1-2|tr|6|1|
tr-g2|tr|6|2|
tt-g1|tt|6|1|
uga-g1|uga|6|1|
uk-comp8|uk|8|-|
uk-g1|uk|6|1|
uk-g1-2|uk|6|1|detailed
ur-g1|ur|6|1|
ur-g2|ur|6|2|
uz-g1|uz|6|1|
ve-g1|ve|6|1|
ve-g2|ve|6|2|
vi-comp8|vi|8|-|vietnam
vi-g0|vi|6|0|vietnam
vi-g1|vi|6|1|saigon
vi-g1-vietnam|vi|6|1|vietnam
vi-g2|vi|6|2|vietnam
xh-g2|xh|6|2|ubc-nguni
yi-g1|yi|6|1|
yue-HK-g1|yue_HK|6|1|
zh-CHN-g1|zh_CHN|6|1|"""

lang_map = {
    'af': 'Afrikaans', 'akk': 'Akkadian', 'ar': 'Arabic', 'as': 'Assamese', 'as_IN': 'Assamese',
    'aw_IN': 'Awadhi', 'awa': 'Awadhi', 'ba': 'Bashkir', 'be': 'Belarusian', 'be_IN': 'Bengali',
    'bg': 'Bulgarian', 'bh': 'Bihari', 'bn': 'Bengali', 'bo': 'Tibetan', 'br_IN': 'Braj',
    'bra': 'Braj', 'ca': 'Catalan', 'chr': 'Cherokee', 'ckb': 'Sorani Kurdish',
    'cmn_CN': 'Chinese Mandarin', 'cmn_TW': 'Bopomofo Chinese', 'cop': 'Coptic',
    'cs': 'Czech', 'cy': 'Welsh', 'da': 'Danish', 'de': 'German', 'dra': 'Dravidian',
    'el': 'Greek', 'en': 'English', 'en_CA': 'English Canada', 'en_GB': 'English UK',
    'en_IN': 'English India', 'en_US': 'English US', 'eo': 'Esperanto', 'es': 'Spanish',
    'es_NO': 'Spanish', 'et': 'Estonian', 'fa': 'Persian', 'fi': 'Finnish', 'fil': 'Filipino',
    'fr': 'French', 'ga': 'Irish', 'gd': 'Scottish Gaelic', 'gez': "Ge'ez", 'gon': 'Gondi',
    'grc_EN': 'Ancient Greek', 'grc_ES': 'Ancient Greek Spanish', 'gu': 'Gujarati',
    'gu_IN': 'Gujarati', 'haw': 'Hawaiian', 'hbo': 'Ancient Hebrew', 'he': 'Hebrew',
    'he_IL': 'Hebrew', 'hi': 'Hindi', 'hi_IN': 'Hindi', 'hr': 'Croatian', 'hu': 'Hungarian',
    'hy': 'Armenian', 'is': 'Icelandic', 'it': 'Italian', 'iu': 'Inuktitut', 'ja': 'Japanese',
    'ka': 'Georgian', 'ka_IN': 'Kannada', 'kha': 'Khasi', 'kk': 'Kazakh', 'km': 'Khmer',
    'kmr': 'Kurmanji Kurdish', 'kn': 'Kannada', 'ko': 'Korean', 'kok': 'Konkani',
    'kru': 'Kurukh', 'ks_IN': 'Kashmiri', 'lg': 'Luganda', 'lo': 'Lao', 'lt': 'Lithuanian',
    'lv': 'Latvian', 'mi': 'Maori', 'mk': 'Macedonian', 'ml': 'Malayalam', 'ml_IN': 'Malayalam',
    'mn': 'Mongolian', 'mn_IN': 'Manipuri', 'mni': 'Manipuri', 'mr': 'Marathi',
    'mr_IN': 'Marathi', 'ms': 'Malay', 'mt': 'Maltese', 'mun': 'Munda', 'mwr': 'Marwari',
    'my': 'Burmese', 'ne': 'Nepali', 'nl': 'Dutch', 'no': 'Norwegian', 'np_IN': 'Nepali',
    'ny': 'Chichewa', 'or': 'Oriya', 'or_IN': 'Oriya', 'ovd': 'Elfdalian', 'pa': 'Punjabi',
    'pi': 'Pali', 'pl': 'Polish', 'pt': 'Portuguese', 'pu_IN': 'Punjabi', 'ro': 'Romanian',
    'ru': 'Russian', 'rw': 'Kinyarwanda', 'sa': 'Sanskrit', 'sa_IN': 'Sanskrit', 'sah': 'Sakha',
    'sd': 'Sindhi', 'se': 'Northern Sami', 'si_IN': 'Sinhala', 'sin': 'Sinhala', 'sk': 'Slovak',
    'sl': 'Slovenian', 'sl_SI': 'Slovenian', 'smi': 'Sami', 'sr': 'Serbian', 'st': 'Sotho',
    'sv': 'Swedish', 'sw_KE': 'Swahili', 'syc': 'Syriac', 'ta': 'Tamil', 'te': 'Telugu',
    'te_IN': 'Telugu', 'th': 'Thai', 'tr': 'Turkish', 'tt': 'Tatar', 'uga': 'Ugaritic',
    'uk': 'Ukrainian', 'ur': 'Urdu', 'uz': 'Uzbek', 've': 'Venda', 'vi': 'Vietnamese',
    'xh': 'Xhosa', 'yi': 'Yiddish', 'yue_HK': 'Hong Kong Cantonese', 'zh_CHN': 'Simplified Chinese'
}

java_code = ["    private static final Map<String, String> TABLE_NAMES = new HashMap<>();", "    static {"]

for line in id_map.strip().split('\n'):
    parts = line.split('|')
    tid, loc, dots, grade, var = parts[0], parts[1], parts[2], parts[3], parts[4] if len(parts)>4 else ''
    
    lang = lang_map.get(loc, loc)
    
    # Overrides
    if tid.startswith('en-') and var == 'ueb':
        name = f"Unified English Grade {grade}"
    elif tid == 'en-g3':
        name = "Unified English Grade 3"
    elif tid.startswith('en-GB-g'):
        name = f"English UK Grade {grade}"
    elif tid.startswith('en-US-g'):
        name = f"English US Grade {grade}"
    elif tid.startswith('en-CA-comp'):
        name = "English Canada Computer Braille"
    elif tid.startswith('en-GB-comp'):
        name = "English UK Computer Braille"
    elif tid.startswith('en-US-comp'):
        name = "English US Computer Braille"
    elif tid.startswith('cmn-CN-g'):
        name = f"Chinese Mandarin Grade {grade}"
    elif tid.startswith('cmn-TW-g'):
        name = f"Bopomofo Chinese Grade {grade}"
    elif tid.startswith('zh-CHN-g'):
        name = f"Simplified Chinese Grade {grade}"
    elif tid.startswith('fil-'):
        name = f"Filipino Grade {grade}"
    elif tid.startswith('vi-g'):
        name = f"Vietnamese Grade {grade}"
    elif tid.startswith('ko-g'):
        name = f"Korean Grade {grade}"
    elif tid.startswith('grc-EN-g'):
        name = f"Ancient Greek Grade {grade}"
    elif tid.startswith('grc-ES-g'):
        name = f"Ancient Greek Spanish Grade {grade}"
    elif tid.startswith('hbo-g'):
        name = f"Ancient Hebrew Grade {grade}"
    elif tid.startswith('da-g'):
        name = f"Danish Grade {grade}"
    elif tid.startswith('da-comp'):
        name = "Danish Computer Braille"
    elif tid.startswith('sv-g'):
        name = f"Swedish Grade {grade}"
    elif tid.startswith('sv-comp'):
        name = "Swedish Computer Braille"
    elif tid.startswith('de-g'):
        name = f"German Grade {grade}"
    elif tid.startswith('de-comp'):
        name = "German Computer Braille"
    elif tid.startswith('sw-KE-g'):
        name = f"Swahili Grade {grade}"
    elif tid == 'kmr-g0': name = "Kurmanji Kurdish Grade 0"
    elif tid == 'ovd-g0': name = "Elfdalian Grade 0"
    elif tid == 'ovd-comp8': name = "Elfdalian Computer Braille"
    elif tid == 'smi-g0': name = "Sami Grade 0"
    elif tid == 'smi-comp8': name = "Sami Computer Braille"
    elif tid == 'nl-g0': name = "Dutch Grade 0"
    elif tid == 'nl-comp8': name = "Dutch Computer Braille"
    elif 'comp' in tid or dots == '8':
        name = f"{lang} Computer Braille"
    else:
        name = f"{lang} Grade {grade}"
        
    java_code.append(f'        TABLE_NAMES.put("{tid}", "{name}");')
    
java_code.append("    }")
with open('tables.txt', 'w') as f:
    f.write('\n'.join(java_code))
print("done")
