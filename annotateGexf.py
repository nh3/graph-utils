#!/usr/bin/env python
'''
Usage: annotate_gexf.py -i <gexf> [-n <node_annot>] [-e <edge_annot>]

Options:
    -i <gexf>           input, read from stdin if omitted
    -n <node_annot>     node annotation
    -e <edge_annot>     edge annotation
'''

from __future__ import print_function
import sys
import signal
import logging
signal.signal(signal.SIGPIPE, signal.SIG_DFL)
logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s; %(levelname)s; %(funcName)s; %(message)s',
        datefmt='%y-%m-%d %H:%M:%S')
import xml.etree.cElementTree as etree
import numpy as np
import pandas as pd

xmlns = {'default':'http://www.gexf.net/1.3', 'viz':'http://www.gexf.net/1.3/viz', 'xsi':'http://www.w3.org/2001/XMLSchema-instance'}
attrs_tag = '{{{}}}attributes'.format(xmlns['default'])
attr_tag = '{{{}}}attribute'.format(xmlns['default'])
attvs_tag = '{{{}}}attvalues'.format(xmlns['default'])
attv_tag = '{{{}}}attvalue'.format(xmlns['default'])

def read_node_annotation(filename):
    if filename is None:
        return {},{}
    dat = pd.read_table(filename)
    ctypes = get_column_type(dat)
    return dat.set_index('node').to_dict(),ctypes

def read_edge_annotation(filename):
    if filename is None:
        return {},{}
    dat = pd.read_table(filename)
    ctypes = get_column_type(dat)
    x = dat.set_index(['source','target']).to_dict()
    y = dat.set_index(['target','source']).to_dict()
    for name in x:
        x[name].update(y[name])
    return x,ctypes

def get_column_type(dat):
    col_types = dict()
    for name in dat.columns:
        ctype = dat[name].dtype
        if np.issubdtype(ctype, np.integer):
            col_types[name] = 'integer'
        elif np.issubdtype(ctype, np.float):
            col_types[name] = 'float'
        else:
            col_types[name] = 'string'
    return col_types

def main(args):
    logging.info(args)
    node_annot,node_ctypes = read_node_annotation(args['n'])
    logging.info(node_ctypes)
    edge_annot,edge_ctypes = read_edge_annotation(args['e'])
    logging.info(edge_ctypes)

    for prefix,uri in xmlns.items():
        if prefix == 'default':
            prefix = ''
        etree.register_namespace(prefix, uri)

    tree = etree.parse(args['i'])
    root = tree.getroot()
    graph = root.find('default:graph', xmlns)
    attributes = graph.find('default:attributes', xmlns)
    if attributes is None:
        attributes = etree.SubElement(graph, attrs_tag, {'class':'node','mode':'static'})
    nodes = graph.find('default:nodes', xmlns)
    edges = graph.find('default:edges', xmlns)

    node_dict = {node.attrib['id']:node.attrib['label'] for node in nodes.iterfind('default:node', xmlns)}

    for name in node_annot:
        attr = etree.SubElement(attributes, attr_tag,
                {'id':name, 'title':name, 'type':node_ctypes[name]})
        logging.info(name)
        for node in nodes.iterfind('default:node', xmlns):
            label = node.attrib['label']
            attvalues = node.find('default:attvalues', xmlns)
            if attvalues is None:
                attvalues = etree.SubElement(node, attvs_tag)
            attv = etree.SubElement(attvalues, attv_tag,
                    {'for':name, 'value':str(node_annot[name][label])})

    for name in edge_annot:
        logging.info(name)
        for edge in edges.iterfind('default:edge', xmlns):
            eid = edge.attrib['id']
            src = edge.attrib['source']
            tgt = edge.attrib['target']
            key = (str(node_dict[src]),str(node_dict[tgt]))
            if key in edge_annot[name]:
                attvalues = edge.find('default:attvalues', xmlns)
                if attvalues is None:
                    attvalues = etree.SubElement(edge, attvs_tag)
                attv = etree.SubElement(attvalues, attv_tag,
                        {'for':name, 'value':str(edge_annot[name][key])})

    tree.write('/dev/stdout', xml_declaration=True)


if __name__ == '__main__':
    from docopt import docopt
    args = docopt(__doc__)
    args = {k.lstrip('-<').rstrip('>'):args[k] for k in args}
    try:
        main(args)
    except KeyboardInterrupt:
        logging.warning('Interrupted')
        sys.exit(1)
