#!/usr/bin/python

import xml.parsers.expat;
import sys;
parser=xml.parsers.expat.ParserCreate('UTF-8');

values_en = {}
values_lang = {}
values_hash = {}
name=''

def parse(lang, values):
  def start_element(n, attrs):
    global name;
    if n != u'string': return
    name=attrs[u'name']
  def end_element(n):
    global name;
    name=''
  def char_data(value):
    global name;
    if name == '': return;
    if not name in values: values[name] = u'';
    values[name] += value;
  p = xml.parsers.expat.ParserCreate()
  p.StartElementHandler = start_element
  p.EndElementHandler = end_element
  p.CharacterDataHandler = char_data
  if lang == 'en':
    f=open('res/values/strings.xml');
  else:
    f=open('res/values-%s/strings.xml' % lang);
  p.ParseFile(f);

parse('en', values_en);
parse(sys.argv[1], values_lang);
page=open('res/raw/key.html').read();
for num,(key,orig) in enumerate(
         sorted(values_en.iteritems(),
	        key=lambda x:len(x[1]), reverse=True)):
  if not key in values_lang: continue;
  replacement = '##//$$$%s$$$//##' % num;
  values_hash[key] = replacement;
  page = page.replace(orig, replacement);

for key,repl in values_lang.iteritems():
  if not key in values_hash: continue;
  orig = values_hash[key];
  replacement = values_lang[key];
  page = page.replace(orig, replacement);
print page.encode('UTF-8')

