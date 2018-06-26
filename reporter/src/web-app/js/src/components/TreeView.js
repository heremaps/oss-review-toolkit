import React from 'react';
import { Tree, Input, Tooltip } from 'antd';
import { connect } from 'react-redux';
import { convertToTableFormat } from '../utils';

const TreeNode = Tree.TreeNode;
const Search = Input.Search;

class TreeView extends React.Component {

    constructor(props){
        super(props);
        this.state = { search: '' };
        if (props.reportData) {
            this.state = {
                ...this.state,
                tree: this.calcTree(props.reportData),
                expandedKeys: [],
            };
        } 
    }

    onExpand = (expandedKeys) => {
        this.setState({
            expandedKeys,
            autoExpandParent: false,
        });
    }

    componentWillReceiveProps(newProps) {
        if (this.props.reportData !== newProps.reportData) {
            this.setState({
                search: '',
                tree: this.calcTree(newProps.reportData)
            })
        }
    }

    calcTree = (reportData) => {
        return convertToTableFormat(reportData);
    }

    onSearchChange = (e) => {
        const { tree } = this.state;
        const searchVal = e.target.value;
        const reccurSearch = (arr, parent, search) => {
            return arr.reduce((acc, item) => {
                const matches = item.name.indexOf(search) >= 0;
                let keys = [];
                if (item.children) {
                    keys = reccurSearch(item.children, item, search);
                }
                if (matches) {
                    keys.push(parent.id || parent.name)
                }
                return [...acc, ...keys];
            }, [])
        }
        if (tree) {
            const keysToExpand = reccurSearch(tree, { key: 'root' }, searchVal);
            this.setState({
                expandedKeys: keysToExpand,
                autoExpandParent: true,
                search: searchVal
            })
        }
    }


    renderTreeNode = (treeLeaf) => {
        const { name } = treeLeaf;
        const { search } = this.state;

        let nameMapped = (<span>{name}</span>);

        if (search && name.indexOf(search) >= 0) {
            const index = name.indexOf(search);
            const pre = name.substring(0, index);
            const post = name.substring(index + search.length);
            nameMapped = (
                <span>
                    {pre}
                    <span style={{
                        color: 'red'
                    }}>{search}</span>
                    {post}
                </span>
            )
        }

        return (<TreeNode
            key={treeLeaf.id || treeLeaf.name}
            title={nameMapped}
            >
            {treeLeaf.children.map((childLeaf) =>
                this.renderTreeNode(childLeaf)  
            )}
            </TreeNode>)
    }

    render() {
        const { tree, expandedKeys, autoExpandParent } = this.state;
        return tree && (
        <div>
            <Search style={{ marginBottom: 8 }} placeholder="Search" onChange={this.onSearchChange} />
            <Tree 
                onExpand={this.onExpand}
                autoExpandParent={autoExpandParent}
                expandedKeys={expandedKeys}>
                {tree.map((rootLeaf) => this.renderTreeNode(rootLeaf))}
            </Tree>
        </div>)
    }    
}

export default connect(
    (state) => ({reportData: state}),
    () => ({})
  )(TreeView);
