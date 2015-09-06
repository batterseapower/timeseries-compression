import numpy as np
import pandas as pd
import sys

# Floats:
#  Snappy baseline: 10894227
#  Gzip baseline: 7683716
# Doubles:
#  Snappy baseline: 12375632
#  Gzip baseline: 8560507


unzip = lambda xys: ([x for x, _ in xys], [y for _, y in xys])

df = pd.read_csv(sys.argv[1], sep='\t', names=['Compressor', 'Exponent', 'Mantissa', 'Size'], dtype={'Compressor': np.object, 'Exponent': np.object, 'Mantissa': np.object, 'Size': np.int32})
df['Exponent Method'], df['Exponent Codec'] = unzip([x.split(' ', 1) if ' ' in x else (x, '[1]') for x in df.pop('Exponent')])
df['Mantissa Method'], df['Mantissa Codec'] = unzip([x.split(' ', 1) for x in df.pop('Mantissa')])

# GZip should dominate Snappy
print pd.DataFrame.pivot_table(df, values='Size', index=['Exponent Method', 'Mantissa Method'], columns=['Compressor'], aggfunc=np.min)
print

#print '==== Gzip Only'
#print
#df = df[df['Compressor'] == 'Gzip']

# Delta or literal better for the exponent/mantissa?
def print_both_ways(df):
	print df.ix[:, 'Delta'].order().head(n=10)
	print df.ix[:, 'Literal'].order().head(n=10)
	print
print_both_ways(pd.DataFrame.pivot_table(df, values='Size', index=['Exponent Codec'], columns=['Exponent Method'], aggfunc=np.min))
print_both_ways(pd.DataFrame.pivot_table(df, values='Size', index=['Mantissa Codec'], columns=['Mantissa Method'], aggfunc=np.min))

# Comparing codecs
print df.groupby(df['Exponent Codec'].apply(lambda c: tuple(set(c[1:-1].split(', '))))).min().ix[:, 'Size']
print df.groupby(df['Mantissa Codec'].apply(lambda c: tuple(set(c[1:-1].split(', '))))).min().ix[:, 'Size']
print