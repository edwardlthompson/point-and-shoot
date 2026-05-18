import struct, sys

TYPE_SIZES = {1:1,2:1,3:2,4:4,5:8,10:8,7:1,11:4,12:8}
TYPE_NAMES = {5:'RAT',10:'SRAT',1:'BYTE',2:'STR',3:'SHORT',4:'LONG',7:'UNDEF',11:'FLOAT',12:'DOUBLE'}
TAG_NAMES  = {50964:'FM1',50965:'FM2',50721:'CM1',50722:'CM2',50728:'ASN',
              256:'Width',257:'Height',50706:'DNGVer',50708:'Model',50740:'SubIFD',
              273:'StripOff',279:'StripCnt',258:'BPS',259:'Compress',262:'Photo'}

def dump_ifd(path, label):
    print(f'=== {label} ===')
    with open(path,'rb') as f:
        data = f.read()
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    for i in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        typ = struct.unpack_from('<H', data, pos+2)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        name = TAG_NAMES.get(tag, str(tag))
        tn   = TYPE_NAMES.get(typ, str(typ))
        sz   = TYPE_SIZES.get(typ, 1)
        total_bytes = cnt * sz
        inline_flag = ' INLINE' if total_bytes <= 4 else ''
        first = ''
        if typ == 10 and not inline_flag and off + 8 <= len(data):
            n0 = struct.unpack_from('<i', data, off)[0]
            d0 = struct.unpack_from('<i', data, off+4)[0]
            first = '  first=%d/%d=%.4f' % (n0, d0, n0/max(abs(d0),1))
        elif typ == 5 and not inline_flag and off + 8 <= len(data):
            n0 = struct.unpack_from('<I', data, off)[0]
            d0 = struct.unpack_from('<I', data, off+4)[0]
            first = '  first=%d/%d=%.4f' % (n0, d0, n0/max(d0,1))
        print('  [%02d] tag=%5d (%8s) type=%-5s cnt=%3d off=%8d%s%s' % (
              i, tag, name, tn, cnt, off, inline_flag, first))
        pos += 12
    print()

paths = [
    ('hfr-runs/v4_uw.dng',   'UW (cam2)'),
    ('hfr-runs/v4_wide.dng', 'Wide (cam3)'),
    ('hfr-runs/v4_tele.dng', 'Tele (cam4)'),
]
for path, label in paths:
    dump_ifd(path, label)
