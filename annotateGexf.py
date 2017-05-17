#!/usr/bin/env python
'''
Usage: annotate_gexf.py -i <gexf> [-n <node_annot>] [-c <color_attr>] [-e <edge_annot>] [-w <weight_attr>] [--force-str]

Options:
    -i <gexf>           input, read from stdin if omitted
    -n <node_annot>     node annotation
    -c <color_attr>     color attribute
    -e <edge_annot>     edge annotation
    -w <weight_attr>    weight attribute
    --force-str         force attributes to have string type
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
color_tag = '{{{}}}color'.format(xmlns['viz'])

def read_node_annotation(filename, forcestr=False):
    if filename is None:
        return {},{}
    if forcestr:
        dat = pd.read_table(filename, dtype=str)
    else:
        dat = pd.read_table(filename)
    ctypes = get_column_type(dat)
    return dat.set_index('node').to_dict(),ctypes

def read_edge_annotation(filename, forcestr=False):
    if filename is None:
        return {},{}
    if forcestr:
        dat = pd.read_table(filename, dtype=str)
    else:
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
    node_annot,node_ctypes = read_node_annotation(args['n'], forcestr=args['force-str'])
    logging.info(node_ctypes)
    edge_annot,edge_ctypes = read_edge_annotation(args['e'], forcestr=args['force-str'])
    logging.info(edge_ctypes)

    for prefix,uri in xmlns.items():
        if prefix == 'default':
            prefix = ''
        etree.register_namespace(prefix, uri)

    tree = etree.parse(args['i'])
    root = tree.getroot()
    graph = root.find('default:graph', xmlns)
    nodes = graph.find('default:nodes', xmlns)
    edges = graph.find('default:edges', xmlns)
    graph.remove(nodes)
    graph.remove(edges)
    attributes = graph.findall('default:attributes', xmlns)
    attr_classes = [x.attrib['class'] for x in attributes]
    if not 'node' in attr_classes:
        node_attributes = etree.SubElement(graph, attrs_tag, {'class':'node','mode':'static'})
    else:
        node_attributes = attributes[attr_classes.index('node')]
    if not 'edge' in attr_classes:
        edge_attributes = etree.SubElement(graph, attrs_tag, {'class':'edge','mode':'static'})
    else:
        edge_attributes = attributes[attr_classes.index('edge')]
    graph.append(nodes)
    graph.append(edges)

    node_dict = {node.attrib['id']:node.attrib['label'] for node in nodes.iterfind('default:node', xmlns)}

    for name in node_annot:
        attr = etree.SubElement(node_attributes, attr_tag,
                {'id':name, 'title':name, 'type':node_ctypes[name]})
        logging.info(name)
        for node in nodes.iterfind('default:node', xmlns):
            label = node.attrib['label']
            if label in node_annot[name]:
                value = str(node_annot[name][label])
            else:
                continue
            if args['c'] is not None and name == args['c']:
                r,g,b,a = value.split(',')
                viz_color = node.find('viz:color', xmlns)
                if viz_color is not None:
                    node.remove(viz_color)
                viz_color = etree.SubElement(node, color_tag, {'r':r,'g':g,'b':b,'a':a})
            else:
                attvalues = node.find('default:attvalues', xmlns)
                if attvalues is None:
                    attvalues = etree.SubElement(node, attvs_tag)
                attv = etree.SubElement(attvalues, attv_tag,
                        {'for':name, 'value':value})

    for name in edge_annot:
        attr = etree.SubElement(edge_attributes, attr_tag,
                {'id':name, 'title':name, 'type':node_ctypes[name]})
        logging.info(name)
        for edge in edges.iterfind('default:edge', xmlns):
            src = edge.attrib['source']
            tgt = edge.attrib['target']
            key = (str(node_dict[src]),str(node_dict[tgt]))
            if key in edge_annot[name]:
                value = str(edge_annot[name][key])
            else:
                continue
            if args['w'] is not None and name == args['w']:
                edge.set('weight', value)
            else:
                attvalues = edge.find('default:attvalues', xmlns)
                if attvalues is None:
                    attvalues = etree.SubElement(edge, attvs_tag)
                attv = etree.SubElement(attvalues, attv_tag,
                        {'for':name, 'value':value})

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
