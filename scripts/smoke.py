#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Run a real HTTP rental workflow in an explicitly disposable test deployment."""
import argparse
import datetime as dt
import http.cookiejar
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid

class Client:
    """Same-origin HTTP session for the project API; never reads browser credentials."""
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.count = 0
        self.token = None
    def request(self, path, method='GET', body=None, expected=200, csrf=True):
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and csrf:
            if self.token is None:
                self.token = self.request('/api/auth/csrf')
            headers[self.token['header']] = self.token['token']
        request = urllib.request.Request(self.base+path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=30) as response:
                status, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, content = error.code, error.read()
        assert status == expected, f'{method} {path}: expected {expected}, got {status}; {content[:300]!r}'
        self.count += 1
        if path.endswith('.csv'):
            return content.decode('utf-8-sig')
        return json.loads(content)
    def login(self, username, password):
        return self.request('/api/auth/login', 'POST', {'username':username,'password':password})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--confirm-test-database', action='store_true', required=True)
    parser.add_argument('--url', default='http://127.0.0.1:18195')
    args = parser.parse_args()
    assert args.confirm_test_database
    env_file = Path(os.environ['RENTAL_QA_ENV'])
    config = dict(line.split('=',1) for line in env_file.read_text().splitlines() if line and not line.startswith('#'))
    client = Client(args.url)
    assert client.request('/actuator/health')['status'] == 'UP'
    client.request('/api/lists/assets', expected=401)
    profile = client.login(config.get('ADMIN_USERNAME','admin'), config['ADMIN_PASSWORD'])
    client.request('/api/bookings','POST',{},expected=403,csrf=False)
    catalogs = client.request('/api/catalog')
    department = profile['departmentId']
    suffix = uuid.uuid4().hex[:8]
    category = client.request('/api/master/categories','POST',{'name':'流程验收 '+suffix,'nameEn':'Workflow QA '+suffix})
    customer = client.request('/api/master/customers','POST',{'name':'示例影像工作室 / Demo Studio '+suffix,'contact':'demo@example.invalid','departmentId':department,'notes':'虚构验收数据 / Fictional test data'})
    assets = []
    for label in ['A','B']:
        assets.append(client.request('/api/master/assets','POST',{'name':'摄影机套件 '+label,'code':'QA-'+suffix+'-'+label,'categoryId':category['id'],'departmentId':department,'dailyRate':20,'deposit':200,'accessories':'电源、连接线、运输箱 / Power supply, cables, case'})['id'])
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    iso = lambda value: value.isoformat().replace('+00:00','Z')
    def draft(start=now-dt.timedelta(hours=1),end=now+dt.timedelta(hours=47)):
        return client.request('/api/bookings','POST',{'customerId':customer['id'],'startAt':iso(start),'endAt':iso(end),'assetIds':assets,'notes':'双机摄影器材租赁 / Two-camera rental'})
    value=draft(); bid=value['booking']['id']; duplicate=draft(); did=duplicate['booking']['id']
    def action(identifier,action,body=None,expected=200):
        return client.request(f'/api/bookings/{identifier}/{action}','POST',dict({'requestKey':str(uuid.uuid4())},**(body or {})),expected=expected)
    action(bid,'confirm'); action(did,'confirm',expected=409)
    action(bid,'checkout',expected=409)
    action(bid,'payment',{'kind':'RENT_RECEIPT','amount':80,'reference':'QA-rent'})
    deposit_body={'requestKey':str(uuid.uuid4()),'kind':'DEPOSIT_RECEIPT','amount':400,'reference':'QA-deposit'}
    client.request(f'/api/bookings/{bid}/payment','POST',deposit_body)
    retry=client.request(f'/api/bookings/{bid}/payment','POST',deposit_body)
    assert len(retry['ledger']) == 2 and retry['totals']['netCash']==480
    action(bid,'checkout')
    value=action(bid,'return',{'lineId':value['lines'][0]['id'],'amount':0,'inspectionPassed':True,'note':'附件齐全，功能正常 / Complete accessories; passed'})
    assert value['booking']['status']=='PARTIAL'
    action(bid,'close',expected=409)
    value=action(bid,'return',{'lineId':value['lines'][1]['id'],'amount':10,'inspectionPassed':False,'note':'附件缺失，待补齐 / Missing accessory; held for repair'})
    assert value['booking']['status']=='RETURNED'
    action(bid,'payment',{'kind':'DEPOSIT_REFUND','amount':400,'reference':'QA-excess-refund'},expected=409)
    action(bid,'payment',{'kind':'DEPOSIT_DEDUCTION','amount':10,'reference':'QA-agreed-damage'})
    action(bid,'payment',{'kind':'DEPOSIT_REFUND','amount':390,'reference':'QA-refund'})
    final=action(bid,'close')
    assert final['booking']['status']=='CLOSED' and final['totals']['rentPaid']==90 and final['totals']['depositHeld']==0 and final['totals']['netCash']==90
    availability=client.request('/api/availability?start='+iso(now+dt.timedelta(days=4))+'&end='+iso(now+dt.timedelta(days=5)))
    assert not next(row for row in availability if row['asset']['id']==assets[1])['available']
    client.request(f'/api/assets/{assets[1]}/maintenance','POST',{'blocked':'false','note':'配件补齐并验收通过 / Repaired and passed'})
    future=draft(now+dt.timedelta(days=4),now+dt.timedelta(days=5));action(future['booking']['id'],'confirm')
    client.request('/api/lists/bookings?search='+final['booking']['number']+'&size=1')
    report=client.request('/api/reports.csv');assert 'zhuatech2' not in report and final['booking']['number'] in report
    for type in ['roles','menus','permissions','departments','dictionaries','settings','audit']:
        assert client.request('/api/lists/'+type)['total']>0
    users=client.request('/api/lists/users');assert 'passwordHash' not in json.dumps(users)
    other=client.request('/api/admin/departments','POST',{'name':'验收隔离部门 '+suffix})
    operator_password=config.get('OPERATOR_PASSWORD','QaA9'+uuid.uuid4().hex)
    operator=client.request('/api/admin/users','POST',{'username':'operator-'+suffix,'displayName':'业务专员 / Operator','password':operator_password,'roleId':2,'departmentId':department,'enabled':True})
    isolated=client.request('/api/admin/users','POST',{'username':'isolated-'+suffix,'displayName':'其他部门 / Other department','password':operator_password,'roleId':2,'departmentId':other['id'],'enabled':True})
    limited=Client(args.url);limited.login(isolated['username'],operator_password)
    limited.request('/api/lists/users',expected=403);limited.request(f'/api/bookings/{bid}',expected=403)
    assert limited.request('/api/lists/bookings')['total']==0
    client.request('/api/dashboard')
    result={'checks':client.count+limited.count,'completeRentalId':bid,'confirmedRentalId':future['booking']['id'],'operatorUsername':operator['username'],'operatorPasswordSource':'OPERATOR_PASSWORD in external QA environment','currency':final['booking']['currency'],'depositHeld':0,'rentPaid':90,'netCash':90,'result':'PASS'}
    print(json.dumps(result,ensure_ascii=False))
    output=env_file.parent/'smoke-result.json';output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');output.chmod(0o600)

if __name__=='__main__':
    main()
