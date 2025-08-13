package org.ngbw.sdk.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;


/**
 * An object that represents a <code>WHERE</code> clause search criterion that uses a
 * <code>VARCHAR</code> value.
 *
 * @author Paul Hoover
 *
 */
class StringCriterion extends ColumnCriterion<String>
{
    protected boolean equalOrLike = true;
    /**
     * Constructs a representation of a search criterion for the given column name and value.
     *
     * @param colName the name of the column
     * @param value   the value of the column
     */
    StringCriterion ( String colName, String value )
    {
        super(colName, value);
    }

    StringCriterion setEqualOrLike(boolean equalOrLike)
    {
        this.equalOrLike = equalOrLike;
        return this;
    }

    /**
     * Sets the value of a parameter in a <code>PreparedStatement</code> object using the name and
     * value given in the constructor.
     *
     * @param statement the <code>PreparedStatement</code> object for which a parameter will be set
     * @param index     the offset that indicates the parameter to set
     *
     * @return the next offset to use when setting parameters
     *
     * @throws SQLException
     */
    @Override
    public int setParameter ( PreparedStatement statement, int index ) throws SQLException
    {
        if (m_value != null)
        {
            statement.setString(index, m_value);
            index += 1;
        }

        return index;
    }

    @Override
    public String getPhrase ()
    {
        if (m_value == null)
        {
            return m_colName + " IS NULL ";
        }
        else
        {
            if (equalOrLike)
                return m_colName + " = ? ";
            else
                return m_colName + " like ? ";
        }
    }

}
