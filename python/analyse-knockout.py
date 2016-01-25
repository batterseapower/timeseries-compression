import numpy as np
import pandas as pd
import argparse
import sys
from matplotlib import pyplot as plt

# python analyse-knockout.py ../floats-knockout
# python analyse-knockout.py ../floats-nosplit.tsv Number

parser = argparse.ArgumentParser()
parser.add_argument('root', help='the root file to be analysed')
parser.add_argument('--html', action='store_true')
args = parser.parse_args()

grid_to_string = str if not args.html else lambda x: (x if isinstance(x, pd.DataFrame) else x.to_frame()).to_html()

dtypes = {'Compressor': np.object, 'Size': np.int32, 'Special Cases': np.object }
df_nosplit = pd.read_csv(args.root + '-nosplit.tsv', sep='\t', names=['Compressor', 'Knockout', 'Number', 'Size'], dtype=dtypes)
df_split = pd.read_csv(args.root + '.tsv', sep='\t', names=['Special Cases', 'Compressor', 'Knockout', 'Exponent', 'Mantissa', 'Size'], dtype=dtypes)
df_split_special, df_split_nospecial = df_split[df_split['Special Cases'] == 'true'], df_split[df_split['Special Cases'] == 'false']

def summarise(df):
    return df.groupby(df['Knockout']).min().ix[:, 'Size']

df = pd.DataFrame.from_dict({
    'No Split': summarise(df_nosplit),
    'Split w/ Special Cases': summarise(df_split_special),
    'Split wout/ Special Cases': summarise(df_split_nospecial),
})

print grid_to_string(df)
df.plot(ylim=(0, df.max().max() * 1.1), marker='x')
plt.savefig('special-cases.png')
plt.show()


