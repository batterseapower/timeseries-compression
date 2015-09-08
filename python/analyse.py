import numpy as np
import pandas as pd
import argparse
import sys

# Floats:
#  Snappy baseline: 10894227
#  Gzip baseline: 7683716
# Doubles:
#  Snappy baseline: 12375632
#  Gzip baseline: 8560507


unzip = lambda xys: ([x for x, _ in xys], [y for _, y in xys])


parser = argparse.ArgumentParser()
parser.add_argument('file', default=sys.stdin, type=argparse.FileType('r'), help='the file to be analysed')
parser.add_argument('fields', help='comma-separated list of fields in file')
parser.add_argument('--only-codecs', help='comma-separated list of codecs to include in analysis')
parser.add_argument('--only-compressors', help='comma-separated list of compressors to include in analysis')
args = parser.parse_args()

fields = args.fields.split(',') # Exponent,Mantissa or Number

dtypes = {'Compressor': np.object, 'Size': np.int32 }
dtypes.update({field: np.object for field in fields})
df = pd.read_csv(args.file, sep='\t', names=['Compressor'] + fields + ['Size'], dtype=dtypes)
for field in fields:
    df[field + ' Method'], df[field + ' Codec'] = unzip([x.split(' ', 1) if ' ' in x else (x, '[1]') for x in df.pop(field)])

if args.only_codecs:
    codecs = set(x.strip() + ']' for x in args.only_compressors.split('],'))
    print '== Restricting to', codecs
    mask = reduce(lambda x, y: x | y, [df[field + ' Codec'] in codecs for field in fields])
    df = df[mask]

print '== GZip should dominate Snappy?'
print pd.DataFrame.pivot_table(df, values='Size', index=[field + ' Method' for field in fields], columns=['Compressor'], aggfunc=np.min)
print

if args.only_compressors:
    compressors = set(args.only_compressors.split(','))
    print '== Restricting to', compressors
    df = df[df['Compressor'].apply(lambda x: x in compressors)]

for field in fields:
    print '== Delta or literal better for the', field.lower() + '?'
    printable_df = pd.DataFrame.pivot_table(df, values='Size', index=[field + ' Codec'], columns=[field + ' Method'], aggfunc=np.min)
    print printable_df.ix[:, 'Delta'].order().head(n=10)
    print printable_df.ix[:, 'Literal'].order().head(n=10)
    print

for field in fields:
    print '== Comparing codecs for the', field.lower()
    print df.groupby(df[field + ' Codec'].apply(lambda c: tuple(set(c[1:-1].split(', '))))).min().ix[:, 'Size'].order()
    print