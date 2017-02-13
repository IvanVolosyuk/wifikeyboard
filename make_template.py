#!/usr/bin/python2
#coding=utf-8

#TODO 用python处理感觉有点蠢，明明可以用java处理的，
import xml.parsers.expat;
import sys;
import re;
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
    f=open('app/src/main/res/values/strings.xml');
  else:
    f=open('app/src/main/res/values-%s/strings.xml' % lang);
  p.ParseFile(f);

def parse_R(file, values):
  try:
    for line in open(file):
      match = re.search(".*public static final int (.*)=0x(.*);", line)
      if match:
        values[match.group(1)] = match.group(2)
  except:
    sys.exit(1)

parse('en', values_en);
parse_R('app/build/generated/source/r/debug/com/volosyukivan/R.java', values_lang);
page=open('html/key.html').read();
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
  replacement = '$' + values_lang[key];
  page = page.replace(orig, replacement);
old = None
try:
  old = open("app/src/main/res/raw/key.html").read();
except:
  pass
if (old != page):
  open("app/src/main/res/raw/key.html", "w").write(page.encode('UTF-8'));
