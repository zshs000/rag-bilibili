import hashlib, json, re, urllib.parse, urllib.request
BVID='BV1AbddBTEUJ'
SESS=5f340aa5%2C1792597618%2C822d9%2A41CjBnpERFidtJwc39a4ar49ujkqR3Dk_65GEKsaOvLM8Qi_fbys_KdqqaoDkBXomk5M4SVjVGTjA2ZWQ0YUpvVnc0bVgycDNYM0IxbU4xYXlDaUotTlIwLWEtOW1nTGVoVmdKbEp5aFpqMmZ4LTZBaEQ0NVNYSjhhUWk0czc2S1FrUUljLWl0XzBBIIEC
JCT=e265d754e697c8f3a893045cf850b5f3
BUVID=8A0DD77C-8B56-04F2-6F51-95457610193736529infoc
MIX=[46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52]
headers={'User-Agent':'Mozilla/5.0','Accept':'application/json','Cookie':f'SESSDATA={SESS}; bili_jct={JCT}; buvid3={BUVID}'}
def get(url):
    req=urllib.request.Request(url,headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode('utf-8')
nav=json.loads(get('https://api.bilibili.com/x/web-interface/nav'))['data']['wbi_img']
raw=(nav['img_url'].split('/')[-1].split('.')[0]+nav['sub_url'].split('/')[-1].split('.')[0])
key=''.join(raw[i] for i in MIX if i < len(raw))[:32]
params={'bvid':BVID,'cid':json.loads(get('https://api.bilibili.com/x/player/pagelist?bvid='+BVID))['data'][0]['cid'],'wts':'%d'%__import__('time').time(),'web_location':'1315873'}
qs='&'.join(f"{k}={urllib.parse.quote(str(v), safe='')}" for k,v in sorted(params.items()))
wrid=hashlib.md5((qs+key).encode()).hexdigest()
player=json.loads(get('https://api.bilibili.com/x/player/wbi/v2?'+qs+'&w_rid='+wrid))
print(json.dumps({'subtitle': player.get('data',{}).get('subtitle',{}), 'view_points_keys': list(player.get('data',{}).keys())}, ensure_ascii=False, indent=2)[:12000])
