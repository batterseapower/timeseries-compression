import numpy as np
import pandas as pd
import ctypes
from datetime import date
from matplotlib import pyplot as plt

def f2l(x):
	return ctypes.cast((ctypes.c_float*1)(x), ctypes.POINTER(ctypes.c_int)).contents.value

vod = pd.read_csv('VOD.csv').set_index('Date')
vod.index = pd.DatetimeIndex(vod.index)

xs = vod.reindex(pd.bdate_range(min(vod.index), max(vod.index)))['Adj Close']
#xs = [(y / x) - 1.0 for x, y in zip(xs, xs[1:])] # Returns
xs = filter(lambda x: x != 0.0 and x == x, xs) # Sane values only 0s/NaNs
xs = map(f2l, xs)

df = pd.DataFrame.from_dict({
		'sign':     [(x & 0x80000000) >> 31 for x in xs],
		'exponent': [(x & 0x7F800000) >> 23 for x in xs],
		'mantissa': [(x & 0x007FFFFF) >>  0 for x in xs],
	})

def show_correlation(mantissa):
	pd.DataFrame.from_dict({
		    'low order':  mantissa.apply(lambda x: (x & 0x00FF) >> 0),
		    'high order': mantissa.apply(lambda x: (x & 0xFF00) >> 8),
	    }).plot(kind='scatter', title='low order bytes correlation', x='low order', y='high order')
	plt.show()

	pd.DataFrame.from_dict({
		    'low order':  mantissa.apply(lambda x: (x & 0x00FF00) >>  8),
		    'high order': mantissa.apply(lambda x: (x & 0x7F0000) >> 16),
	    }).plot(kind='scatter', title='high order bytes correlation', x='low order', y='high order')
	plt.show()

mantissa_delta = pd.Series([x - y for x, y in zip(df.mantissa[1:], df.mantissa)])

show_correlation(df['mantissa'])
show_correlation(mantissa_delta)

for c in ['sign', 'exponent', 'mantissa']:
	df[c].plot(title=c)
	plt.show()

mantissa_delta.plot(title='Mantissa Delta')
plt.show()

mantissa_delta.plot(kind='kde', title='Distribution of Mantissa Delta')
plt.show()

modal_exponent = df['exponent'].value_counts().idxmax()
mants = sorted(set(df[df.exponent == modal_exponent]['mantissa']))
print pd.Series([y - x for x, y in zip(mants, mants[1:])]).value_counts()
